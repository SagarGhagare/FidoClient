/*
 * Copyright 2015 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ebayopensource.fidouafclient.curl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.net.ssl.HostnameVerifier;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import android.net.http.HttpResponseCache;
import android.os.AsyncTask;
import android.util.Log;

public class Curl {

	public static String toStr(HttpResponse response) {
		String result = "";
		try {
			InputStream in = response.getEntity().getContent();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(in));
			StringBuilder str = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				str.append(line + "\n");
			}
			in.close();
			result = str.toString();
		} catch (Exception ex) {
			result = "Error";
		}
		return result;
	}
	
	public static String getInSeparateThread(String url) {
		return getInSeparateThread(url, "");
	}

	public static String getInSeparateThread(String url, String header) {
		return getInSeparateThread(url, header, "");
	}

	public static String getInSeparateThread(String url, String header, String redirect_match) {
		GetAsyncTask async = new GetAsyncTask();
		async.execute(url, header, redirect_match);
		while (!async.isDone()){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return async.getResult();
	}

	public static String postInSeparateThread(String url, String header, String data) {
		PostAsyncTask async = new PostAsyncTask();
		async.execute(url, header, data);
		while (!async.isDone()){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return async.getResult();
	}

	public static String putInSeparateThread(String url, String header, String data) {
		PutAsyncTask async = new PutAsyncTask();
		async.execute(url, header, data);
		while (!async.isDone()){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return async.getResult();
	}

	public static String deleteInSeparateThread(String url) {
		DeleteAsyncTask async = new DeleteAsyncTask();
		async.execute(url);
		while (!async.isDone()){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return async.getResult();
	}

	public static String get(String url) {
		return get(url,new String[0]);
	}

	public static String get(String url, String header) {
		return get(url, header, "");
	}
	public static String get(String url, String header, String redirect_match) {
		Log.d("CURL: ", "Entered CURL get with url: " + url + " and header: " + header + " and redirect_match: " + redirect_match);
		if (header != null) {
			Log.d("CURL: ", "header is not null so splitting");
			String headers[] = header.split(" ");
			Log.d("CURL: ", "There are " + headers.length + " headers");
			return get(url, headers, redirect_match);
		}
		else
		{
			return get(url, new String[0], redirect_match);
		}
	}

	public static String get(String url, String[] header) {
		return get(url, header, "");
	}
	public static String get(String url, String[] header, String redirect_match) {
		Log.d("CURL: ", "Entered CURL get with url: " + url + " and headers length: " + header.length + " and redirect_match: " + redirect_match);
		String ret = "";
		try {

			HttpClient httpClient = getClient(url);
			// HTTP parameters to prevent redirects.
			HttpParams params = new BasicHttpParams();
			params.setParameter("http.protocol.handle-redirects",false);

			HttpGet request = new HttpGet(url);
			request.setParams(params);
			try {
				if (header != null && header.length>0){
					for (String h : header){
						try {
							String[] split = h.split(":");
							request.addHeader(split[0], split[1]);
						}
						catch (Exception hex)
						{

						}
					}
				}
				HttpResponse response = httpClient.execute(request);
				Header[] headers = response.getAllHeaders();
				if (response.getStatusLine().getStatusCode() == 302 && response.containsHeader("Location"))
				{
					Log.d("CURL: ", "Got a 302 response with Location header. Following redirect if location does not match redirect_match");
					String location = response.getFirstHeader("Location").getValue();
					Log.d("CURL: ", "Location is " + location);
					if (location.startsWith(redirect_match)){
						Log.d("CURL: ", "Matched redirect - sending location back as the response");
						return location;
					}
					else
					{
						Log.d("CURL: ", "Did not match redirect - following location with new http request");
						if (location.indexOf("://") == -1)
						{
							//need to prefix the location with the original host
							String host = url.substring(0, url.indexOf("/",10)+1);
							Log.d("CURL: ", "host to prefix the url is " + host);
							location = host + location;
						}
						return get(location, header, redirect_match);
					}
				}
				ret = Curl.toStr(response);

			} catch (Exception ex) {
				ex.printStackTrace();
				ret = "{'error_code':'connect_fail','url':'" + url + "'}";
			}
		} catch (Exception e) {
			e.printStackTrace();
			ret = "{'error_code':'connect_fail','e':'" + e + "'}";
		}

		return ret;
	}
	
	public static String post(String url, String header, String data) {
		return post (url, header.split(" "), data);
	}
	
	public static String post(String url, String[] header, String data) {
		Log.d("CURL: ", "Entered CURL post with url: " + url + " and headers length: " + header.length + " and data: " + data);
		String ret = "";
		try {

			HttpClient httpClient = getClient(url);

			HttpPost request = new HttpPost(url);
			if (header != null){
				for (String h : header){
					String[] split = h.split(":");
					if (split.length >1) request.addHeader(split[0], split[1].replace("&nbsp;"," "));
				}
			}
			request.setEntity(new StringEntity(data));
			try {
				HttpResponse response = httpClient.execute(request);
				ret = Curl.toStr(response);
				Header[] headers = response.getAllHeaders();

			} catch (Exception ex) {
				ex.printStackTrace();
				ret = "{'error_code':'connect_fail','url':'" + url + "'}";
			}
		} catch (Exception e) {
			e.printStackTrace();
			ret = "{'error_code':'connect_fail','e':'" + e + "'}";
		}

		return ret;
	}

	public static String put(String url, String header, String data) {
		return put (url, header.split(" "), data);
	}
	public static String put(String url, String[] header, String data) {
		String ret = "";
		try {

			HttpClient httpClient = getClient(url);

			HttpPut request = new HttpPut(url);
			if (header != null && header.length>0){
				for (String h : header){
					String[] split = h.split(":");
					if (split.length >1) request.addHeader(split[0], split[1]);
				}
			}
			request.setEntity(new StringEntity(data));
			try {
				HttpResponse response = httpClient.execute(request);
				ret = Curl.toStr(response);
				Header[] headers = response.getAllHeaders();

			} catch (Exception ex) {
				ex.printStackTrace();
				ret = "{'error_code':'connect_fail','url':'" + url + "'}";
			}
		} catch (Exception e) {
			e.printStackTrace();
			ret = "{'error_code':'connect_fail','e':'" + e + "'}";
		}

		return ret;
	}

	public static String delete(String url) {
		String ret = "";
		try {

			HttpClient httpClient = getClient(url);

			HttpDelete request = new HttpDelete(url);
			try {
				HttpResponse response = httpClient.execute(request);
				ret = Curl.toStr(response);
				Header[] headers = response.getAllHeaders();

			} catch (Exception ex) {
				ex.printStackTrace();
				ret = "{'error_code':'connect_fail','url':'" + url + "'}";
			}
		} catch (Exception e) {
			e.printStackTrace();
			ret = "{'error_code':'connect_fail','e':'" + e + "'}";
		}

		return ret;
	}

	private static HttpClient getClient(String url) {
		HttpClient httpClient = new DefaultHttpClient();
		if (url.toLowerCase().startsWith("https")) {
			httpClient = createHttpsClient();
		}
		return httpClient;
	}

	private static HttpClient createHttpsClient() {
		HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
		SchemeRegistry registry = new SchemeRegistry();
		SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
		socketFactory
				.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
		registry.register(new Scheme("https", socketFactory, 443));
		HttpClient client = new DefaultHttpClient();
		SingleClientConnManager mgr = new SingleClientConnManager(
				client.getParams(), registry);
		DefaultHttpClient httpClient = new DefaultHttpClient(mgr,
				client.getParams());
		return httpClient;
	}

}

class GetAsyncTask extends AsyncTask<String, Integer, String>{

	private String result = null;
	private boolean done = false;
	public boolean isDone() {
		return done;
	}
	public String getResult() {
		return result;
	}
	@Override
	protected String doInBackground(String... args) {
		result = Curl.get(args[0], args[1], args[2]);
		done = true;
		return result;
	}
	protected void onProgressUpdate(Integer... progress) {
    }
    protected void onPostExecute(String result) {
		this.result = result;
		done = true;
	}
}

class PostAsyncTask extends AsyncTask<String, Integer, String>{

	private String result = null;
	private boolean done = false;
	public boolean isDone() {
		return done;
	}
	public String getResult() {
		return result;
	}
	@Override
	protected String doInBackground(String... args) {
		result = Curl.post(args[0],args[1],args[2]);//(url, header, data)
		done = true;
		return result;
	}
	protected void onProgressUpdate(Integer... progress) {
    }
	@Override
	protected void onPostExecute(String result) {
		this.result = result;
		done = true;
	}
}

class PutAsyncTask extends AsyncTask<String, Integer, String>{

	private String result = null;
	private boolean done = false;
	public boolean isDone() {
		return done;
	}
	public String getResult() {
		return result;
	}
	@Override
	protected String doInBackground(String... args) {
		result = Curl.put(args[0],args[1],args[2]);//(url, header, data)
		done = true;
		return result;
	}
	protected void onProgressUpdate(Integer... progress) {
	}
	@Override
	protected void onPostExecute(String result) {
		this.result = result;
		done = true;
	}
}

class DeleteAsyncTask extends AsyncTask<String, Integer, String>{

	private String result = null;
	private boolean done = false;
	public boolean isDone() {
		return done;
	}
	public String getResult() {
		return result;
	}
	@Override
	protected String doInBackground(String... args) {
		result = Curl.delete(args[0]);//(url)
		done = true;
		return result;
	}
	protected void onProgressUpdate(Integer... progress) {
	}
	@Override
	protected void onPostExecute(String result) {
		this.result = result;
		done = true;
	}
}
