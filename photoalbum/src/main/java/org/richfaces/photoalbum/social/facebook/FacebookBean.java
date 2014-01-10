/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.richfaces.photoalbum.social.facebook;

import java.io.Serializable;
import java.util.List;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.richfaces.json.JSONObject;
import org.richfaces.photoalbum.util.ListUtils;

@Named
@SessionScoped
public class FacebookBean implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private JSONObject userInfo;

    private List<String> userAlbumIds;

    public List<String> getUserAlbumIds() {
        return userAlbumIds;
    }

    public String getUserAlbumString() {
        return ListUtils.sListToString(userAlbumIds);
    }

    public void setAlbumIds(String ids) {
        this.userAlbumIds = ListUtils.StringToSList(ids);
    }

    public JSONObject getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(JSONObject userInfo) {
        this.userInfo = userInfo;
    }
    
    public String getUserId() {
        return userInfo != null ? userInfo.optString("id", "1") : "1";
    }
}
