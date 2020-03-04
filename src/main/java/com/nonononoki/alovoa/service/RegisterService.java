package com.nonononoki.alovoa.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Date;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nonononoki.alovoa.Tools;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserDates;
import com.nonononoki.alovoa.entity.UserRegisterToken;
import com.nonononoki.alovoa.model.RegisterDto;
import com.nonononoki.alovoa.repo.GenderRepository;
import com.nonononoki.alovoa.repo.UserDatesRepository;
import com.nonononoki.alovoa.repo.UserIntentionRepository;
import com.nonononoki.alovoa.repo.UserRegisterTokenRepository;
import com.nonononoki.alovoa.repo.UserRepository;

@Service
public class RegisterService {
	
	private final String TEMP_EMAIL_FILE_NAME = "temp-mail.txt";
	
	@Value("${app.token.length}")
	private int tokenLength;
	
	@Value("${app.age.min}")
	private int minAge;
	
	@Value("${app.age.max}")
	private int maxAge;
	
	@Value("${app.age.range}")
	private int ageRange;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	@Autowired
	private MailService mailService;
	
	@Autowired
	private UserRepository userRepo;
	
	@Autowired
	private UserDatesRepository userDatesRepo;
	
	@Autowired
	private GenderRepository genderRepo;
	
	@Autowired
	private UserIntentionRepository userIntentionRepo;
	
	@Autowired
	private UserRegisterTokenRepository registerTokenRepo;
	
	@Autowired
	private CaptchaService captchaService;
	
	@Autowired
	private HttpServletRequest request;

	public void register(RegisterDto dto) throws Exception {
		
		//check minimum age
		LocalDate now = LocalDate.now();
		Period period = Period.between(dto.getDateOfBirth(), now);
		int userAge = period.getYears();
		if(userAge < minAge) {
			throw new Exception("");
		}
		
		boolean isValid = captchaService.isValid(dto.getCaptchaId(), dto.getCaptchaText());
		if(!isValid) {
			throw new Exception("");
		}
		
		User user = userRepo.findByEmail(dto.getEmail().toLowerCase());
		if(user != null) {
			throw new Exception("");
		}
		user = new User();
		user.setEmail(dto.getEmail().toLowerCase());
		user.setPassword(passwordEncoder.encode(dto.getPassword()));
		user.setFirstName(dto.getFirstName());
		int userMinAge = userAge - ageRange;
		int userMaxAge = userAge + ageRange;
		if(userMinAge < minAge) {
			userMinAge = minAge;
		}
		if(userMaxAge > maxAge) {
			userMaxAge = maxAge;
		}
		//user.setAge(userAge);
		user.setPreferedMinAge(userMinAge);
		user.setPreferedMaxAge(userMaxAge);
		user.setGender(genderRepo.findById(dto.getGender()).orElse(null));
		if(user.getGender() == null) {
			throw new Exception("");
		}
		user.setIntention(userIntentionRepo.findById(dto.getIntention()).orElse(null));
		
		//check if email is in spam mail list
		try {
			if(Tools.isTextContainingLineFromFile(Tools.getFileFromResources(TEMP_EMAIL_FILE_NAME), user.getEmail())) {
				throw new Exception("");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		user = userRepo.save(user);
		UserDates dates = new UserDates();	
		Date today = new Date();
		dates.setActiveDate(today);
		dates.setCreationDate(today);
		dates.setDateOfBirth(dto.getDateOfBirth());
		dates.setIntentionChangeDate(today);
		dates.setMessageCheckedDate(today);
		dates.setMessageDate(today);
		dates.setNotificationCheckedDate(today);
		dates.setNotificationDate(today);
		dates.setUser(user);
		dates = userDatesRepo.saveAndFlush(dates);
		user.setDates(dates);
		user = userRepo.save(user);
		
		createUserToken(user);
	}
	
	public void createUserToken(User user) throws MessagingException {
		UserRegisterToken token = generateToken(user);
		user.setRegisterToken(token);
		user = userRepo.save(user);
		mailService.sendRegistrationMail(user, token);
	}
	
	public UserRegisterToken generateToken(User user) {
		UserRegisterToken token = new UserRegisterToken();
		token.setContent(RandomStringUtils.randomAlphanumeric(tokenLength));
		token.setCreationDate(new Date());
		token.setUser(user);
		return registerTokenRepo.saveAndFlush(token);
	}
	
	public boolean registerConfirm(String tokenString) {
		UserRegisterToken token = registerTokenRepo.findByContent(tokenString);
		
		if(token == null) {
			return false;
		}
		
		User user = token.getUser();
		
		if(user == null) {
			return false;
		}
		
		if(user.isConfirmed()) {
			return false;
		}
		
		user.setConfirmed(true);
		user.setRegisterToken(null);
		userRepo.saveAndFlush(user);
		return true;
	}
}
