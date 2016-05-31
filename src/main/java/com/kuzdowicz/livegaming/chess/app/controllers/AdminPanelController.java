package com.kuzdowicz.livegaming.chess.app.controllers;

import java.util.Date;
import java.util.List;

import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.kuzdowicz.livegaming.chess.app.constants.UserRoles;
import com.kuzdowicz.livegaming.chess.app.forms.dto.EditForm;
import com.kuzdowicz.livegaming.chess.app.forms.dto.SignUpForm;
import com.kuzdowicz.livegaming.chess.app.models.UserAccount;
import com.kuzdowicz.livegaming.chess.app.props.Messages;
import com.kuzdowicz.livegaming.chess.app.repositories.UsersRepository;

@Controller
public class AdminPanelController {

	private UsersRepository usersRepository;
	private PasswordEncoder passwordEncoder;
	private static final Logger logger = Logger.getLogger(AdminPanelController.class);

	@Autowired
	public AdminPanelController(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
		this.usersRepository = usersRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@RequestMapping(value = "admin/users", method = RequestMethod.GET)
	public ModelAndView getAllUsers() {

		logger.info("getAllUsers()");
		List<UserAccount> users = usersRepository.findAll();

		ModelAndView usersPage = new ModelAndView("users");
		usersPage.addObject("users", users);
		addBasicObjectsToModelAndView(usersPage);

		return usersPage;
	}

	@RequestMapping(value = "admin/users/editUser", method = RequestMethod.GET)
	public ModelAndView showEditUserForm(@RequestParam("login") String login, String errorMessage,
			String successMessage) {

		logger.info("showEditUserForm()");
		UserAccount user = usersRepository.findOneByUsername(login);

		ModelAndView userDetailPage = new ModelAndView("editUser");
		EditForm editForm = new EditForm();
		userDetailPage.addObject("editForm", editForm);
		userDetailPage.addObject("user", user);
		userDetailPage.addObject("errorMessage", errorMessage);
		userDetailPage.addObject("successMessage", successMessage);
		addBasicObjectsToModelAndView(userDetailPage);

		return userDetailPage;
	}

	@RequestMapping(value = "admin/users/editUser", method = RequestMethod.POST)
	public ModelAndView sendEditUserData(@Valid @ModelAttribute("editForm") EditForm editForm, BindingResult result) {

		logger.info("sendEditUserData()");

		Boolean changePasswordFlag = editForm.getChangePasswordFlag();
		Boolean changePasswordCheckBoxIsUnchecked = !changePasswordFlag;
		if (changePasswordCheckBoxIsUnchecked) {
			if (result.hasFieldErrors("email") || result.hasFieldErrors("name") || result.hasFieldErrors("lastname")) {
				ModelAndView editFormSite = new ModelAndView("editUser");
				editFormSite.addObject("changePasswordCheckBoxIsChecked", changePasswordFlag);
				editFormSite.addObject("editForm", editForm);
				return editFormSite;
			}
		} else {
			if (result.hasErrors()) {
				ModelAndView editFormSite = new ModelAndView("editUser");
				editFormSite.addObject("changePasswordCheckBoxIsChecked", changePasswordFlag);
				editFormSite.addObject("editForm", editForm);
				return editFormSite;
			}
		}

		String userLogin = editForm.getUsername();
		String name = editForm.getName();
		String lastname = editForm.getLastname();

		String email = editForm.getEmail();
		String password = editForm.getPassword();
		String confirmPassword = editForm.getConfirmPassword();

		if (changePasswordFlag && !password.equals(confirmPassword)) {

			return showEditUserForm(userLogin, Messages.getProperty("error.passwords.notequal"), null);
		}

		UserAccount user = usersRepository.findOneByUsername(userLogin);

		if (user != null) {

			if (!StringUtils.isBlank(name)) {
				user.setName(name);
			}
			if (!StringUtils.isBlank(lastname)) {
				user.setLastname(lastname);
			}

			if (changePasswordFlag) {
				try {
					String hashedPassword = passwordEncoder.encode(password).toString();
					user.setPassword(hashedPassword);
				} catch (Exception e) {
					logger.debug(e);
				}
			}
			user.setEmail(email);

			Boolean adminFlag = editForm.getGrantAdminAuthorities();

			if (adminFlag) {
				user.setRole(UserRoles.ADMIN.geNumericValue());
			} else {
				user.setRole(UserRoles.USER.geNumericValue());
			}

			user.setEmail(email);
			usersRepository.save(user);

			return showEditUserForm(user.getUsername(), null, Messages.getProperty("success.user.edit"));

		} else {

			return showEditUserForm(null, Messages.getProperty("error.fatal.error"), null);
		}
	}

	@RequestMapping(value = "admin/users/remove", method = RequestMethod.POST)
	public ModelAndView removeUser(@RequestParam("username") String username) {
		logger.info("removeUser()");

		UserAccount user = usersRepository.findOneByUsername(username);
		usersRepository.delete(user);

		return getAllUsers();
	}

	@RequestMapping(value = "/admin/users/addUser", method = RequestMethod.GET)
	public ModelAndView getAddUserForm(String errorMsg, String successMsg) {
		logger.info("getAddUserForm()");

		ModelAndView addUserPage = new ModelAndView("addUser");
		addUserPage.addObject("errorMessage", errorMsg);
		addUserPage.addObject("successMsg", successMsg);
		SignUpForm signUpForm = new SignUpForm();
		addUserPage.addObject("signUpForm", signUpForm);
		addBasicObjectsToModelAndView(addUserPage);

		return addUserPage;
	}

	@RequestMapping(value = "admin/users/addUser", method = RequestMethod.POST)
	public ModelAndView addUser(@Valid @ModelAttribute("signUpForm") SignUpForm signUpForm, BindingResult result) {
		logger.debug("addUser()");

		if (result.hasErrors()) {
			ModelAndView addUserPage = new ModelAndView("addUser");
			addUserPage.addObject("signUpForm", signUpForm);
			return addUserPage;
		}

		String userLogin = signUpForm.getUsername();
		String plaintextPassword = signUpForm.getPassword();
		String confirmPassword = signUpForm.getConfirmPassword();
		Boolean adminFlag = signUpForm.getGrantAdminAuthorities();
		String email = signUpForm.getEmail();

		// validation
		if (!plaintextPassword.equals(confirmPassword)) {

			return getAddUserForm(Messages.getProperty("error.passwords.notequal"), null);
		}

		UserAccount user = new UserAccount();
		user.setUsername(userLogin);

		if (adminFlag) {
			user.setRole(UserRoles.ADMIN.geNumericValue());
		} else {
			user.setRole(UserRoles.USER.geNumericValue());
		}

		try {
			String hashedPassword = passwordEncoder.encode(plaintextPassword);
			user.setPassword(hashedPassword);
		} catch (Exception e) {
			logger.debug(e);
		}

		user.setEmail(email);
		user.setIsRegistrationConfirmed(true);
		user.setRegistrationDate(new Date());

		UserAccount updateResult = usersRepository.insert(user);

		if (updateResult != null) {
			return getAddUserForm(null, Messages.getProperty("success.user.created"));
		} else {
			return getAddUserForm("login " + userLogin + " allready exists!", null);
		}

	}

	private void addBasicObjectsToModelAndView(ModelAndView modelAndView) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		String userLogin = auth.getName();
		modelAndView.addObject("currentUserName", userLogin);

	}

}