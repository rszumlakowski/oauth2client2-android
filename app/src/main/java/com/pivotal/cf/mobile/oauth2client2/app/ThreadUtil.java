package com.pivotal.cf.mobile.oauth2client2.app;

import android.os.Handler;
import android.os.Looper;

/**
 * Collection of utility methods to assist with thread logic.
 */
public class ThreadUtil {

	/**
	 * Simplifies the "sleep" call included in the {@link Thread} class by wrapping the try/catch logic. Yields the current thread for the specified duration.
	 * 
	 * @param durationInMilliseconds
	 *            Sleep duration in milliseconds.
	 */
	public static void sleep(long durationInMilliseconds) {
		try {
			Thread.sleep(durationInMilliseconds);
		} catch (InterruptedException e) {
		}
	}

	/**
	 * @return The handler returned will be configured to run on the UI thread.
	 */
	public static Handler getUIThreadHandler() {
		return new Handler(Looper.getMainLooper());
	}
	
	public static boolean isUIThread() {
		return (Looper.myLooper() == Looper.getMainLooper());
	}
}
