// __BEGIN_LICENSE__
// Copyright (C) 2008-2010 United States Government as represented by
// the Administrator of the National Aeronautics and Space Administration.
// All Rights Reserved.
// __END_LICENSE__

package gov.nasa.arc.geocam.talk.service;

import gov.nasa.arc.geocam.talk.R;
import gov.nasa.arc.geocam.talk.bean.ServerResponse;
import gov.nasa.arc.geocam.talk.exception.AuthenticationFailedException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import roboguice.inject.InjectResource;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.inject.Inject;

/**
 * The Class SiteAuthCookie.
 */
public class SiteAuthCookie implements ISiteAuth {

	/** The server root url. */
	@InjectResource(R.string.url_server_root)
	String serverRootUrl;
	
	/** The app path. */
	@InjectResource(R.string.url_relative_app)
	String appPath;
	
	/** The shared preferences. */
	@Inject SharedPreferences sharedPreferences;

	/** The http client. */
	private DefaultHttpClient httpClient;
	
	/** The session id cookie. */
	private Cookie sessionIdCookie;
	
	/** The context. */
	private Context context;
	
	/** The username. */
	private String username;
	
	/** The password. */
	private String password;		
	
	/** The intent helper. */
	@Inject IIntentHelper intentHelper;
	
	/**
	 * Instantiates a new site auth cookie.
	 *
	 * @param context the context
	 */
	@Inject
	public SiteAuthCookie(Context context) {
		this.context = context;
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#setRoot(java.lang.String)
	 */
	@Override
	public void setRoot(String siteRoot) {
		serverRootUrl = siteRoot;
		
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#post(java.lang.String, java.util.Map)
	 */
	@Override
	public ServerResponse post(String relativePath, Map<String, String> params)
			throws AuthenticationFailedException, IOException, ClientProtocolException,
			InvalidParameterException {
		return post(relativePath, params, null);
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#post(java.lang.String, java.util.Map, byte[])
	 */
	@Override
	public ServerResponse post(String relativePath, Map<String, String> params, byte[] audioBytes)
			throws AuthenticationFailedException, IOException, ClientProtocolException,
			InvalidParameterException {
		if (params == null) {
			throw new InvalidParameterException("Post parameters are required");
		}
		
		ensureAuthenticated();

		httpClient = new DefaultHttpClient();

		HttpParams httpParams = httpClient.getParams();
		HttpClientParams.setRedirecting(httpParams, false);

		HttpPost post = new HttpPost(this.serverRootUrl + "/" + appPath + "/" + relativePath);
		post.setParams(httpParams);

		HttpEntity httpEntity;
		
		if (audioBytes != null) {
			httpEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

			for (String key : params.keySet()) {
				((MultipartEntity) httpEntity).addPart(key, new StringBody(params.get(key)));
			}
			if (audioBytes != null) {
				((MultipartEntity) httpEntity).addPart("audio", new ByteArrayBody(audioBytes,
						"audio/mpeg", "audio.mp4"));
			}
		} else {
			List<BasicNameValuePair> nameValuePairs = new ArrayList<BasicNameValuePair>();

			for (String key : params.keySet()) {
				nameValuePairs.add(new BasicNameValuePair(key, params.get(key)));
			}

			httpEntity = new UrlEncodedFormEntity(nameValuePairs, HTTP.ASCII);
		}
		
		post.setEntity(httpEntity);
		httpClient.getCookieStore().addCookie(sessionIdCookie);
		ServerResponse sr = new ServerResponse(httpClient.execute(post));
		
		if(sr.getResponseCode() == 401 || sr.getResponseCode() == 403)
		{
			throw new AuthenticationFailedException("Server responded with code: " + sr.getResponseCode());
		}
		
		return sr;
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#get(java.lang.String, java.util.Map)
	 */
	@Override
	public ServerResponse get(String relativePath, Map<String, String> params)
			throws AuthenticationFailedException, IOException, ClientProtocolException {
		ensureAuthenticated();
		httpClient = new DefaultHttpClient();

		HttpGet get = new HttpGet(this.serverRootUrl + "/" + appPath + "/" + relativePath);

		// TODO: add param parsing and query string construction as necessary

		httpClient.getCookieStore().addCookie(sessionIdCookie);
		// get.setHeader("Cookie", sessionIdCookie.toString());

		ServerResponse sr = new ServerResponse(httpClient.execute(get));
		
		if(sr.getResponseCode() == 401 || sr.getResponseCode() == 403)
		{
			throw new AuthenticationFailedException("Server responded with code: " + sr.getResponseCode());
		}
		
		return sr;
	}
	
	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#getAudioFile(java.lang.String, java.util.Map)
	 */
	@Override
	public String getAudioFile(String relativePath, Map<String, String> params)
			throws AuthenticationFailedException, IOException, ClientProtocolException {
		ensureAuthenticated();

		httpClient = new DefaultHttpClient();
		
		HttpPost post = new HttpPost(this.serverRootUrl + relativePath);
		// TODO: add param parsing and query string construction as necessary

		httpClient.getCookieStore().addCookie(sessionIdCookie);

		HttpResponse r = httpClient.execute(post);
		
		if(r.getStatusLine().getStatusCode() == 401 || r.getStatusLine().getStatusCode() == 403)
		{
			throw new AuthenticationFailedException("Server responded with code: " + r.getStatusLine().getStatusCode());
		}
		
        if(r.getFirstHeader("Content-Type").getValue().contains("mp4"))
        {
        	FileOutputStream ostream = new FileOutputStream(context.getFilesDir().toString() + "/tempfile.mp4");
            r.getEntity().writeTo(ostream);
        }
        
        return (context.getFilesDir().toString() + "/tempfile.mp4");
        
	}

	/**
	 * Ensure that the user is currently logged in.
	 *
	 * @throws AuthenticationFailedException the authentication failed exception
	 * @throws ClientProtocolException the client protocol exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void ensureAuthenticated() throws AuthenticationFailedException,
			ClientProtocolException, IOException {
		
		String new_username = sharedPreferences.getString("username", null);
		String new_password = sharedPreferences.getString("password", null);

		if (username == null || password == null || new_username == null || new_password == null) {
			username = new_username;
			password = new_password;
			Log.i("Talk", "Username and / or password are null");
			reAuthenticate();
		} else if ( !username.contentEquals(new_username) || !password.contentEquals(new_password))
		{
			reAuthenticate();
			Log.i("Talk", "Username and / or password have changed");
		}
		
		if (username == null || password == null) {
			throw new AuthenticationFailedException("Username and/or password not set.");
		} else {
			Date now = new Date();
			if (sessionIdCookie == null || sessionIdCookie.isExpired(now)) {
				login();
			}
		}
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#login()
	 */
	@Override
	public void login() throws ClientProtocolException,
			IOException, AuthenticationFailedException {
		
		username = sharedPreferences.getString("username", null);
		password = sharedPreferences.getString("password", null);
		
		httpClient = new DefaultHttpClient();
		HttpParams params = httpClient.getParams();
		HttpClientParams.setRedirecting(params, false);

		HttpPost p = new HttpPost(serverRootUrl + "/accounts/login/");
		p.setParams(params);

		List<BasicNameValuePair> nameValuePairs = new ArrayList<BasicNameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("username", username));
		nameValuePairs.add(new BasicNameValuePair("password", password));

		p.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.ASCII));

		HttpResponse r = httpClient.execute(p);
		if (302 == r.getStatusLine().getStatusCode()) {
			for (Cookie c : httpClient.getCookieStore().getCookies()) {
				if (c.getName().contains("sessionid")) {
					sessionIdCookie = c;
					intentHelper.RegisterC2dm();
					return;
				}
			}
			throw new AuthenticationFailedException(
					"Session cookie was missing from server login response.");
		} else {
			throw new AuthenticationFailedException("Got unexpected response code from server: "
					+ r.getStatusLine().getStatusCode());
		}
	}
	
	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#logoutAndUnregister()
	 */
	@Override
	public void logoutAndUnregister()throws AuthenticationFailedException, ClientProtocolException, IOException
	{
		httpClient = new DefaultHttpClient();
		
		HttpGet g = new HttpGet(serverRootUrl + "/" + appPath + "/" + "unregister");
		httpClient.execute(g);
		
		httpClient = new DefaultHttpClient();
		
		HttpParams params = httpClient.getParams();
		HttpClientParams.setRedirecting(params, false);

		HttpPost p = new HttpPost(serverRootUrl + "/accounts/logout/");
		p.setParams(params);

		List<BasicNameValuePair> nameValuePairs = new ArrayList<BasicNameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("username", username));
		nameValuePairs.add(new BasicNameValuePair("password", password));

		p.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.ASCII));

		HttpResponse r = httpClient.execute(p);

		sharedPreferences.edit().remove("username").commit();
		sharedPreferences.edit().remove("password").commit();

		if (302 == r.getStatusLine().getStatusCode()) {
			sessionIdCookie = null;
			return;
		} else {
			throw new AuthenticationFailedException("Got unexpected response code from server: "
					+ r.getStatusLine().getStatusCode());
		}
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#reAuthenticate()
	 */
	@Override
	public void reAuthenticate() throws ClientProtocolException, AuthenticationFailedException, IOException {
		sessionIdCookie = null;
	}

	/* (non-Javadoc)
	 * @see gov.nasa.arc.geocam.talk.service.ISiteAuth#isLoggedIn()
	 */
	@Override
	public boolean isLoggedIn(){
		
		if(sessionIdCookie == null) return false;
		
		String username = sharedPreferences.getString("username", null);
		String password = sharedPreferences.getString("password", null);

		if ( username == null || password == null)
			return false;
		
		return true;
	}
}
