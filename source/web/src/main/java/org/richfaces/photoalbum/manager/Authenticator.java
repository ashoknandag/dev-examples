/**
 * License Agreement.
 *
 *  JBoss RichFaces - Ajax4jsf Component Library
 *
 * Copyright (C) 2007  Exadel, Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1 as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 */
package org.richfaces.photoalbum.manager;

/**
 * Class encapsulated all functionality, related to working with authenticating/registering users.
 *
 * @author Andrey Markhel
 */
import java.io.File;
import java.io.Serializable;

import javax.enterprise.context.ConversationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;

import org.jboss.seam.security.Credentials;
import org.jboss.seam.security.Identity;
import org.picketlink.idm.impl.api.PasswordCredential;
import org.richfaces.photoalbum.domain.User;
import org.richfaces.photoalbum.event.EventType;
import org.richfaces.photoalbum.event.EventTypeQualifier;
import org.richfaces.photoalbum.event.Events;
import org.richfaces.photoalbum.event.NavEvent;
import org.richfaces.photoalbum.event.SimpleEvent;
import org.richfaces.photoalbum.service.Constants;
import org.richfaces.photoalbum.service.IUserAction;
import org.richfaces.photoalbum.util.Environment;
import org.richfaces.photoalbum.util.HashUtils;
import org.richfaces.photoalbum.util.Utils;

@ConversationScoped
public class Authenticator implements Serializable {

    private static final long serialVersionUID = -4585673256547342140L;

    @Inject
    LoggedUserTracker userTracker;

    // @In @Out
    @Inject
    User user;

    @Inject
    Identity identity;

    @Inject
    Credentials credentials;

    @Inject
    IUserAction userAction;

    private boolean loginFailed = false;

    private boolean conversationStarted = false;

    @Inject
    @Any
    Event<SimpleEvent> event;
    @Inject
    @EventType(Events.UPDATE_MAIN_AREA_EVENT)
    Event<NavEvent> navEvent;

    @Inject
    FileManager fileManager;

    /**
     * Method, that invoked when user try to login to the application.
     *
     * @return boolean indicator, that denotative is user succesfully loginned to the system.
     *
     */
    public boolean authenticate() {
        try {
            // If user with this login and password exist, the user object will be returned
            user = userAction.login(credentials.getUsername(),
                HashUtils.hash(((PasswordCredential) credentials.getCredential()).getValue()));
            if (user != null) {
                // This check is actual only on livedemo server to prevent hacks.
                // Check if pre-defined user login.
                if (Environment.isInProduction() && user.isPreDefined()) {
                    // If true assume that login failed
                    loginFailed();
                    user = new User();
                    return false;
                }
                // Remove previous session id from users store
                userTracker.removeUserId(user.getId());
                // Mark current user as actual
                userTracker.addUserId(user.getId(), Utils.getSession().getId());
                identity.addRole(Constants.ADMIN_ROLE, "Users", "GROUP");
                // Raise event to controller to update Model
                // Events.instance().raiseEvent(Constants.AUTHENTICATED_EVENT, user);
                event.select(new EventTypeQualifier(Events.AUTHENTICATED_EVENT)).fire(new SimpleEvent());
                // Login was succesfull
                setLoginFailed(false);
                return true;
            }
        } catch (Exception nre) {
            loginFailed();
            return false;
        }
        return false;
    }

    /**
     * Method, that invoked when user logout from application.
     *
     * @return outcome string to redirect.
     *
     */
    public String logout() {
        identity.logout();
        // Remove user from users store
        userTracker.removeUserId(user.getId());
        setConversationStarted(false);
        return Constants.LOGOUT_OUTCOME;
    }

    public void resetCredentials() {
        credentials.clear();
    }

    /**
     * Method, that invoked when user try to register in the application. If registration was successfull, user immediately will
     * be loginned to the system.
     *
     * @param user - user object, that will be passed to registration procedure.
     *
     */
    public void register(User user) {
        // Checks
        if (checkPassword(user) || checkUserExist(user) || checkEmailExist(user.getEmail())) {
            return;
        }
        user.setPasswordHash(HashUtils.hash(user.getPassword()));
        // This check is actual only on livedemo server to prevent hacks.
        // Only admins can mark user as pre-defined
        user.setPreDefined(false);
        if (!handleAvatar(user)) {
            return;
        }
        try {
            userAction.register(user);
        } catch (Exception e) {
            event.select(new EventTypeQualifier(Events.ADD_ERROR_EVENT)).fire(new SimpleEvent(Constants.REGISTRATION_ERROR));
            return;
        }
        // Registration was successfull, so we can login this user.
        credentials.setCredential(new PasswordCredential(user.getPassword()));
        credentials.setUsername(user.getLogin());
        // try {
        // identity.authenticate();
        // } catch (LoginException e) {
        // Events.instance().raiseEvent(Constants.ADD_ERROR_EVENT, Constants.LOGIN_ERROR);
        // }
        String response = identity.login();
        if (!response.equals("RESPONSE_LOGIN_SUCCESS")) {
            event.select(new EventTypeQualifier(Events.ADD_ERROR_EVENT)).fire(new SimpleEvent(Constants.LOGIN_ERROR));
        }

    }

    /**
     * Method, that invoked when user want to go to the registration screen
     *
     */
    public void goToRegister() {
        // create new User object
        user = new User();
        // Clear avatarData component in conversation scope
        Contexts.getConversationContext().set(Constants.AVATAR_DATA_COMPONENT, null);
        setLoginFailed(false);
        // raise event to controller to prepare Model.
        event.select(new EventTypeQualifier(Events.START_REGISTER_EVENT)).fire(new SimpleEvent());
    }

    /**
     * Method, that invoked when new conversation is started. This method prevent instantiation of couples of conversations when
     * user refresh the whole page.
     *
     * @return string outcome to properly redirect on the current page(to assign to page parameters conversationId.
     */
    public String startConversation() {
        navEvent.fire(new NavEvent(NavigationEnum.ANONYM));
        setConversationStarted(true);
        return "";
    }

    private boolean handleAvatar(User user) {
        File avatarData = (File) Contexts.getConversationContext().get(Constants.AVATAR_DATA_COMPONENT);
        if (avatarData != null) {
            user.setHasAvatar(true);
            // FileManager fileManager = (FileManager) Contexts.getApplicationContext().get(Constants.FILE_MANAGER_COMPONENT);
            if (fileManager == null || !fileManager.saveAvatar(avatarData, user)) {
                event.select(new EventTypeQualifier(Events.ADD_ERROR_EVENT)).fire(
                    new SimpleEvent(Constants.AVATAR_SAVING_ERROR));
                return false;
            }
        }
        return true;
    }

    private boolean checkUserExist(User user) {
        if (userAction.isUserExist(user.getLogin())) {
            Utils.addFacesMessage(Constants.REGISTER_LOGIN_NAME_ID, Constants.USER_WITH_THIS_LOGIN_ALREADY_EXIST);
            return true;
        }
        return false;
    }

    private boolean checkEmailExist(String email) {
        if (userAction.isEmailExist(email)) {
            Utils.addFacesMessage(Constants.REGISTER_EMAIL_ID, Constants.USER_WITH_THIS_EMAIL_ALREADY_EXIST);
            return true;
        }
        return false;
    }

    private boolean checkPassword(User user) {
        if (!user.getPassword().equals(user.getConfirmPassword())) {
            Utils.addFacesMessage(Constants.REGISTER_CONFIRM_PASSWORD_ID, Constants.CONFIRM_PASSWORD_NOT_EQUALS_PASSWORD);
            return true;
        }
        return false;
    }

    private void loginFailed() {
        setLoginFailed(true);
        // facesMessages.clear();
        // facesMessages.add(Constants.INVALID_LOGIN_OR_PASSWORD);
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(Constants.INVALID_LOGIN_OR_PASSWORD));
        FacesContext.getCurrentInstance().renderResponse();
    }

    public boolean isLoginFailed() {
        return loginFailed;
    }

    public void setLoginFailed(boolean loginFailed) {
        this.loginFailed = loginFailed;
    }

    public boolean isConversationStarted() {
        return conversationStarted;
    }

    public void setConversationStarted(boolean conversationStarted) {
        this.conversationStarted = conversationStarted;
    }
}