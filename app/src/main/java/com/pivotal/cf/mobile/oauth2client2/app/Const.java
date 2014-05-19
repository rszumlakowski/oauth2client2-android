package com.pivotal.cf.mobile.oauth2client2.app;

public class Const {
    public static final String USER_ID = "rob";
    public static final String CLIENT_ID = "947045348411-lf2k2rpp7gmhgonthk2l39221ei36ugq.apps.googleusercontent.com";
    public static final String CLIENT_SECRET = "cYvu_h-koYhJYYzKk7aEXpxr";
    public static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/auth";
    public static final String REDIRECT_URL = "https://mobile.cf.pivotal.com/oauth2callback";
    public static final String TOKEN_URL = "https://accounts.google.com/o/oauth2/token";
    public static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo";
    public static final String[] SCOPES = new String[] {"profile", "email"};
}
