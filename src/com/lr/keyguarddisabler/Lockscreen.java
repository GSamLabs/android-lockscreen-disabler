package com.lr.keyguarddisabler;

import android.content.Context;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Lockscreen implements IXposedHookLoadPackage, IXposedHookZygoteInit {

	Class<?> securityModeEnum;
	Context ctx;
	XSharedPreferences prefs;
	boolean runningState;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		// TODO Auto-generated method stub
		prefs = new XSharedPreferences("com.lr.keyguarddisabler");
		prefs.makeWorldReadable();
	}

	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

		Object activityThread = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
		ctx = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");

		// Code for complete deactivation
		XposedHelpers.findAndHookMethod("com.android.internal.widget.LockPatternUtils", lpparam.classLoader, "isLockScreenDisabled", new XC_MethodReplacement() {

			@Override
			protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
				// TODO Auto-generated method stub
				if (prefs.getString("lockscreentype", "slide").equals("none")) {
					return true;
				} else {
					return false;
				}
			}
		});

		// possible Code for pre Android 4.3 Lockscreen?
		XposedHelpers.findAndHookMethod("com.android.internal.widget.LockPatternUtils", lpparam.classLoader, "isSecure", new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if (prefs.getString("lockscreentype", "slide").equals("slide")) {
					param.setResult(false);
				}
			}
		});

		// Code for Slide-To-Unlock
		// Android 4.4
		if (lpparam.packageName.contains("android.keyguard")) {

			XposedBridge.log("Keyguard Disabler: Loading Kitkat specific code");

			hookGetSecurityMode(lpparam, "com.android.keyguard.KeyguardSecurityModel$SecurityMode", "com.android.keyguard.KeyguardSecurityModel", "Android 4.4");

		}

		// Android < 4.4
		else if (lpparam.packageName.contains("android.internal")) {

			XposedBridge.log("Keyguard Disabler: Loading Jellybean specific code");
			hookGetSecurityMode(lpparam, "com.android.internal.policy.impl.keyguard.KeyguardSecurityModel$SecurityMode", "com.android.internal.policy.impl.keyguard.KeyguardSecurityModel", "Android < 4.4");
		}

		// HTC
		else if (lpparam.packageName.contains("com.htc.lockscreen")) {

			XposedBridge.log("Keyguard Disabler: Loading HTC specific code");

			try {
				XposedBridge.log("Keyguard Disabler: Loading HTC specific code - trying new Sense 5.x Code - thanks to XDA @Sirlatrom");
				hookGetSecurityMode(lpparam, "com.htc.lockscreen.HtcKeyguardSecurityModel$SecurityMode", "com.htc.lockscreen.HtcKeyguardSecurityModel", "HTC Sense 5.x");
				
			} catch (ClassNotFoundError ex) {
				XposedBridge.log("Keyguard Disabler: Loading HTC specific code - trying new Sense 6 Code - thanks to XDA @Sirlatrom");
				hookGetSecurityMode(lpparam, "com.htc.lockscreen.keyguard.KeyguardSecurityModel$SecurityMode", "com.htc.lockscreen.keyguard.KeyguardSecurityModel",
						"HTC Sense 6");
			}
		}
	}

	private void hookGetSecurityMode(LoadPackageParam lpparam,
			final String nestedClassName, final String outerClassName,
			final String variant) throws ClassNotFoundError {
		securityModeEnum = XposedHelpers.findClass(nestedClassName, lpparam.classLoader);
		XposedBridge.log("SecurityEnum (" + variant + ") found and parsed");
		XposedHelpers.findAndHookMethod(outerClassName, lpparam.classLoader, "getSecurityMode", new XC_MethodHook() {
		
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if (prefs.getString("lockscreentype", "slide").equals("slide")) {
					TelephonyManager manager = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
					if (manager.getSimState() == TelephonyManager.SIM_STATE_PIN_REQUIRED) {
						XposedBridge.log("SIM PIN REQUIRED (" + variant + ")");
						param.setResult(XposedHelpers.getStaticObjectField(securityModeEnum, "SimPin"));
					} else if (manager.getSimState() == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
						XposedBridge.log("SIM PUK REQUIRED (" + variant + ")");
						param.setResult(XposedHelpers.getStaticObjectField(securityModeEnum, "SimPuk"));
					} else {
						XposedBridge.log("Returning NO Security (" + variant + ")");
						param.setResult(XposedHelpers.getStaticObjectField(securityModeEnum, "None"));
					}
				}
			}
		});
	}
}