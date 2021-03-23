package com.nonononoki.alovoa;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.component.TextEncryptorConverter;
import com.nonononoki.alovoa.entity.Captcha;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserDeleteToken;
import com.nonononoki.alovoa.model.RegisterDto;
import com.nonononoki.alovoa.model.UserDeleteAccountDto;
import com.nonononoki.alovoa.model.UserDto;
import com.nonononoki.alovoa.model.UserGdpr;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.MessageRepository;
import com.nonononoki.alovoa.repo.UserBlockRepository;
import com.nonononoki.alovoa.repo.UserHideRepository;
import com.nonononoki.alovoa.repo.UserLikeRepository;
import com.nonononoki.alovoa.repo.UserNotificationRepository;
import com.nonononoki.alovoa.repo.UserReportRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.CaptchaService;
import com.nonononoki.alovoa.service.MessageService;
import com.nonononoki.alovoa.service.RegisterService;
import com.nonononoki.alovoa.service.SearchService;
import com.nonononoki.alovoa.service.UserService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class UserTest {

	@Autowired
	private RegisterService registerService;

	@Autowired
	private CaptchaService captchaService;

	@Autowired
	private UserService userService;

	@Autowired
	private SearchService searchService;

	@Autowired
	private MessageService messageService;

	@Autowired
	private UserRepository userRepo;

	@Autowired
	private UserLikeRepository userLikeRepo;

	@Autowired
	private UserHideRepository userHideRepo;

	@Autowired
	private UserBlockRepository userBlockRepo;

	@Autowired
	private UserReportRepository userReportRepo;

	@Autowired
	private UserNotificationRepository userNotificationRepo;

	@Autowired
	private ConversationRepository conversationRepo;

	@Autowired
	private MessageRepository messageRepo;

	@Autowired
	private TextEncryptorConverter textEncryptor;
	
	@Autowired
	private ObjectMapper objectMapper;

	@Value("${app.age.min}")
	private int minAge;

	@Value("${app.age.max}")
	private int maxAge;
	
	@Value("${app.message.size}")
	private int maxMessageSize;


	@MockBean
	private AuthService authService;

	private final int INTENTION_TEST = 1;
	
	private final String INTEREST = "interest";

	private static List<User> testUsers = null;
	
	private static int user1Age = 18;
	private static int user2Age = 20;
	private static int user3Age = 30;
	
	


	public static List<User> getTestUsers(CaptchaService captchaService, RegisterService registerService)
			throws Exception {
		if (testUsers == null) {
			// register and confirm test users
			Captcha c1 = captchaService.generate();
			RegisterDto user1Dto = createTestUserDto(1, c1, "test1", user1Age);
			String tokenContent1 = registerService.register(user1Dto);
			User user1 = registerService.registerConfirm(tokenContent1);

			Captcha c2 = captchaService.generate();
			RegisterDto user2Dto = createTestUserDto(2, c2, "test2", user2Age);
			String tokenContent2 = registerService.register(user2Dto);
			User user2 = registerService.registerConfirm(tokenContent2);

			Captcha c3 = captchaService.generate();
			RegisterDto user3Dto = createTestUserDto(2, c3, "test3", user3Age);
			String tokenContent3 = registerService.register(user3Dto);
			User user3 = registerService.registerConfirm(tokenContent3);

			testUsers = new ArrayList<>();
			testUsers.add(user1);
			testUsers.add(user2);
			testUsers.add(user3);
		}

		return testUsers;
	}

	public static void deleteAllUsers(UserService userService, AuthService authService, CaptchaService captchaService,
			ConversationRepository conversationRepo, UserRepository userRepo) throws Exception {
		if (testUsers != null) {
			for (User user : testUsers) {
				if (!user.isAdmin()) {
					user = userRepo.findById(user.getId()).get();
					Mockito.when(authService.getCurrentUser()).thenReturn(user);
					UserDeleteToken token = userService.deleteAccountRequest();
					token.setActiveDate(new Date());
					user.setDeleteToken(token);
					userRepo.saveAndFlush(user);
					UserDeleteAccountDto dto = new UserDeleteAccountDto();
					Captcha captcha = captchaService.generate();
					dto.setCaptchaId(captcha.getId());
					dto.setCaptchaText(captcha.getText());
					dto.setConfirm(true);
					dto.setEmail(user.getEmail());
					dto.setTokenString(token.getContent());
					userService.deleteAccountConfirm(dto);
				}
			}
			testUsers = null;
		}
	}

	private static RegisterDto createTestUserDto(long gender, Captcha c, String email, int age) throws IOException {
		RegisterDto dto = new RegisterDto();
		dto.setEmail(email + Tools.MAIL_TEST_DOMAIN);
		dto.setDateOfBirth(Tools.ageToDate(age));
		dto.setPassword("test123");
		dto.setFirstName("test");
		dto.setGender(gender);
		if (c != null) {
			dto.setCaptchaId(c.getId());
			dto.setCaptchaText(c.getText());
		} else {
			dto.setCaptchaId(0);
			dto.setCaptchaText("test");
		}
		dto.setTermsConditions(true);
		dto.setPrivacy(true);
		return dto;
	}

	@Test
	public void test() throws Exception {

		// one default admin user
		Assert.assertEquals(userRepo.count(), 1);

		List<User> testUsers = getTestUsers(captchaService, registerService);
		User user1 = testUsers.get(0);
		User user2 = testUsers.get(1);
		User user3 = testUsers.get(2);

		// set location manually since no extra service is needed
		user1.setLocationLatitude(0.0);
		user1.setLocationLongitude(0.0);

		user2.setLocationLatitude(0.0);
		user2.setLocationLongitude(0.0);

		user3.setLocationLatitude(0.0);
		user3.setLocationLongitude(0.0);

		userRepo.saveAndFlush(user1);
		userRepo.saveAndFlush(user2);
		userRepo.saveAndFlush(user3);

		Assert.assertEquals(userRepo.count(), 4);

		String imgMime = "webp";
		// setup settings
		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		String img1 = Tools.imageToB64(Tools.getFileFromResources("img/profile1.png"), imgMime);
		userService.updateProfilePicture(img1);
		userService.addInterest(INTEREST);
		userService.updateDescription("description1");
		userService.updateIntention(INTENTION_TEST);
		userService.updateMaxAge(100);
		userService.updateMinAge(16);
		userService.updatePreferedGender(2, true);

		Mockito.when(authService.getCurrentUser()).thenReturn(user2);
		String img2 = Tools.imageToB64(Tools.getFileFromResources("img/profile2.png"), imgMime);
		userService.updateProfilePicture(img2);
		userService.addInterest(INTEREST);
		userService.updateDescription("description2");
		userService.updateIntention(INTENTION_TEST);
		userService.updateMaxAge(100);
		userService.updateMinAge(16);
		userService.updatePreferedGender(1, true);

		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		String img3 = Tools.imageToB64(Tools.getFileFromResources("img/profile3.png"), imgMime);
		userService.updateProfilePicture(img3);
		Assert.assertTrue("profile_picture", user3.getProfilePicture() != null);
		userService.addInterest(INTEREST);
		Assert.assertTrue("interest", user3.getInterests().size() == 1);
		String description = "description3";
		userService.updateDescription(description);
		Assert.assertTrue("description", user3.getDescription().equals(description));
		userService.updateIntention(INTENTION_TEST);
		Assert.assertTrue("intention", user3.getIntention().getId() == INTENTION_TEST);
		userService.updateMaxAge(maxAge);
		Assert.assertTrue("max_age", user3.getPreferedMaxAge() == maxAge);

		userService.updateMinAge(minAge);
		Assert.assertTrue("min_age", user3.getPreferedMinAge() == minAge);
		userService.updatePreferedGender(1, true);
		Assert.assertTrue(user3.getPreferedGenders().size() == 1);
		userService.updatePreferedGender(2, true);
		Assert.assertTrue(user3.getPreferedGenders().size() == 2);
		userService.updatePreferedGender(2, false);
		Assert.assertTrue(user3.getPreferedGenders().size() == 1);
		
		
		userService.deleteInterest(authService.getCurrentUser().getInterests().get(0).getId());
		Assert.assertTrue("interest", authService.getCurrentUser().getInterests().size() == 0);
		userService.addInterest(INTEREST);
		userService.addImage(img3);
		Assert.assertTrue("image", authService.getCurrentUser().getImages().size() == 1);
		userService.deleteImage(authService.getCurrentUser().getImages().get(0).getId());
		Assert.assertTrue("image", authService.getCurrentUser().getImages().size() == 0);
		userService.deleteProfilePicture();
		Assert.assertTrue("profile_picture", authService.getCurrentUser().getProfilePicture() == null);
		userService.updateProfilePicture(img3);
		Assert.assertTrue("profile_picture", authService.getCurrentUser().getProfilePicture() != null);
		userService.updateAudio(Tools.resourceToB64("audio/file_example_MP3_700KB.mp3"), "mpeg");
		Assert.assertTrue("audio", user3.getAudio() != null);
		userService.deleteAudio();
		Assert.assertTrue("audio", user3.getAudio() == null);

		searchTest(user1, user2, user3);

		Assert.assertThrows(Exception.class, () -> {
			deleteTest(user1);
		});
		
		//USERDATA 
		ResponseEntity<Resource> userData = userService.getUserdata();
		InputStream inputStream = ((ByteArrayResource) userData.getBody()).getInputStream();
		String userDataString = new BufferedReader(
			      new InputStreamReader(inputStream, StandardCharsets.UTF_8))
			        .lines()
			        .collect(Collectors.joining("\n"));
		UserGdpr gdpr = objectMapper.readValue(userDataString, UserGdpr.class);
		Assert.assertTrue(gdpr.getDescription().equals(authService.getCurrentUser().getDescription()));
		Assert.assertTrue(gdpr.getEmail().equals(authService.getCurrentUser().getEmail()));
		Assert.assertTrue(gdpr.getFirstName().equals(authService.getCurrentUser().getFirstName()));
		//Assert.assertTrue(gdpr.getDates().equals(authService.getCurrentUser().getDates()));
		Assert.assertTrue(gdpr.getDonations().equals(authService.getCurrentUser().getDonations()));
		//Assert.assertTrue(gdpr.getGender().equals(authService.getCurrentUser().getGender()));
		//Assert.assertTrue(gdpr.getIntention().equals(authService.getCurrentUser().getIntention()));
		Assert.assertTrue(gdpr.getInterests().equals(authService.getCurrentUser().getInterests()));
		Assert.assertTrue(gdpr.getLocationLatitude().equals(authService.getCurrentUser().getLocationLatitude()));
		Assert.assertTrue(gdpr.getLocationLongitude().equals(authService.getCurrentUser().getLocationLongitude()));
		Assert.assertTrue(gdpr.getMessageSent().equals(authService.getCurrentUser().getMessageSent()));
		Assert.assertTrue(gdpr.getNumberProfileViews() == authService.getCurrentUser().getNumberProfileViews());
		Assert.assertTrue(gdpr.getNumberSearches() == authService.getCurrentUser().getNumberSearches());
		//Assert.assertTrue(gdpr.getPreferedGenders().equals(authService.getCurrentUser().getPreferedGenders()));
		Assert.assertTrue(gdpr.getPreferedMaxAge() == (authService.getCurrentUser().getPreferedMaxAge()));
		Assert.assertTrue(gdpr.getTotalDonations() == (authService.getCurrentUser().getTotalDonations()));
		Assert.assertTrue(gdpr.getWebPush().equals(authService.getCurrentUser().getWebPush()));
		
		UserTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);

		Assert.assertEquals(conversationRepo.count(), 0);
		Assert.assertEquals(userRepo.count(), 1);

	}
	
	private void deleteTest(User user) throws Exception {
		UserDeleteToken token = userService.deleteAccountRequest();
		UserDeleteAccountDto dto = new UserDeleteAccountDto();
		Captcha captcha = captchaService.generate();
		dto.setCaptchaId(captcha.getId());
		dto.setCaptchaText(captcha.getText());
		dto.setConfirm(true);
		dto.setEmail(user.getEmail());
		dto.setTokenString(token.getContent());
		userService.deleteAccountConfirm(dto);
	}

	private void searchTest(User user1, User user2, User user3) throws Exception {

		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		List<UserDto> searchDtos1 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(searchDtos1.size(), 2);

		Mockito.when(authService.getCurrentUser()).thenReturn(user2);
		List<UserDto> searchDtos2 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(searchDtos2.size(), 1);

		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		List<UserDto> searchDtos3 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(searchDtos3.size(), 1);

		// Tip: 1 degree equals roughly 111km
		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		userService.updatePreferedGender(1, true);
		userService.updatePreferedGender(2, true);
		userService.updatePreferedGender(3, true);

		Mockito.when(authService.getCurrentUser()).thenReturn(user2);
		userService.updatePreferedGender(1, true);
		userService.updatePreferedGender(2, true);
		userService.updatePreferedGender(3, true);

		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		userService.updatePreferedGender(1, true);
		userService.updatePreferedGender(2, true);
		userService.updatePreferedGender(3, true);

		List<UserDto> searchDtos4 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(searchDtos4.size(), 2);

		List<UserDto> searchDtos5 = searchService.search(0.45, 0.0, 50, 1);
		Assert.assertEquals(searchDtos5.size(), 2);

		List<UserDto> searchDtos6 = searchService.search(0.46, 0.0, 50, 1);
		Assert.assertEquals(searchDtos6.size(), 0);
		
		//search filtered by interest
		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		userService.deleteInterest(user1.getInterests().get(0).getId());
		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		List<UserDto> interestSearchDto1 = searchService.search(0.0, 0.0, 50, SearchService.SORT_INTEREST);
		Assert.assertEquals(interestSearchDto1.size(), 1);
		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		userService.addInterest(INTEREST);		
		
		//test preferedAge
		Mockito.when(authService.getCurrentUser()).thenReturn(user2);
		userService.updateMinAge(user1Age+1);
		List<UserDto> ageSearchDto1 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(ageSearchDto1.size(), 1);
		userService.updateMaxAge(user3Age-1);
		List<UserDto> ageSearchDto2 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(ageSearchDto2.size(), 0);
		
		user2.getDates().setDateOfBirth(Tools.localDateToDate(LocalDateTime.now().minusYears(minAge).toLocalDate()));
		Mockito.when(authService.getCurrentUser()).thenReturn(user2);
		userService.updateMinAge(minAge);
		userService.updateMaxAge(maxAge);
		List<UserDto> ageSearchDto3 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(ageSearchDto3.size(), 0);
		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		List<UserDto> ageSearchDto4 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(ageSearchDto4.size(), 1);
		user2.getDates().setDateOfBirth(Tools.ageToDate(user2Age));
		
		// likeUser
		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		userService.likeUser(UserDto.encodeId(user1.getId(), textEncryptor));
		Assert.assertEquals(userLikeRepo.count(), 1);
		Assert.assertEquals(user3.getLikes().size(), 1);
		Assert.assertEquals(userNotificationRepo.count(), 1);
		List<UserDto> searchDtos7 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(searchDtos7.size(), 1);

		// hideUser
		userService.hideUser(UserDto.encodeId(user2.getId(), textEncryptor));
		Assert.assertEquals(userHideRepo.count(), 1);
		Assert.assertEquals(user3.getHiddenUsers().size(), 1);
		List<UserDto> searchDtos8 = searchService.search(0.0, 0.0, 50, 1);
		Assert.assertEquals(searchDtos8.size(), 0);

		user3.getHiddenUsers().clear();
		userRepo.saveAndFlush(user3);
		Assert.assertEquals(userHideRepo.count(), 0);

		// blockUser
		userService.blockUser(UserDto.encodeId(user2.getId(), textEncryptor));
		Assert.assertEquals(userBlockRepo.count(), 1);
		Assert.assertEquals(user3.getBlockedUsers().size(), 1);
		Assert.assertThrows(Exception.class, () -> {
			// cannot like user when blocked
			userService.likeUser(UserDto.encodeId(user2.getId(), textEncryptor));
		});

		userService.unblockUser(UserDto.encodeId(user2.getId(), textEncryptor));
		Assert.assertEquals(userBlockRepo.count(), 0);

		// like back
		Assert.assertThrows(Exception.class, () -> {
			userService.likeUser(UserDto.encodeId(user3.getId(), textEncryptor));
		});

		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		userService.likeUser(UserDto.encodeId(user3.getId(), textEncryptor));

		Assert.assertEquals(userLikeRepo.count(), 2);
		Assert.assertEquals(userNotificationRepo.count(), 2);
		Assert.assertEquals(conversationRepo.count(), 1);
		Assert.assertEquals(user1.getConversations().size(), 1);

		messageService.send(user1.getConversations().get(0), "Hello");
		Assert.assertEquals(messageRepo.count(), 1);

		String verylongString = StringUtils.repeat("*", maxMessageSize);
		messageService.send(user1.getConversations().get(0), verylongString);

		Assert.assertEquals(messageRepo.count(), 2);

		Assert.assertThrows(Exception.class, () -> {
			messageService.send(user1.getConversations().get(0), verylongString + "a");
		});

		Assert.assertEquals(messageRepo.count(), 2);

		// test sending message to blocked users
		userService.blockUser(UserDto.encodeId(user3.getId(), textEncryptor));
		Assert.assertEquals(userBlockRepo.count(), 1);
		Assert.assertThrows(Exception.class, () -> {
			messageService.send(user3.getConversations().get(0), "Hello");
		});

		Assert.assertEquals(messageRepo.count(), 2);

		Mockito.when(authService.getCurrentUser()).thenReturn(user3);
		Assert.assertThrows(Exception.class, () -> {
			messageService.send(user1.getConversations().get(0), "Hello");
		});

		Assert.assertEquals(messageRepo.count(), 2);

		Mockito.when(authService.getCurrentUser()).thenReturn(user1);
		userService.unblockUser(UserDto.encodeId(user3.getId(), textEncryptor));
		Assert.assertEquals(userBlockRepo.count(), 0);

		messageService.send(user1.getConversations().get(0), verylongString);
		Assert.assertEquals(messageRepo.count(), 3);

		Assert.assertEquals(userReportRepo.count(), 0);
		Captcha captchaReport = captchaService.generate();
		userService.reportUser(UserDto.encodeId(user3.getId(), textEncryptor), captchaReport.getId(),
				captchaReport.getText(), "report");
		Assert.assertEquals(userReportRepo.count(), 1);
	}
}
