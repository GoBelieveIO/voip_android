/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.logging.Logger;
import java.util.logging.Level;

/** Java wrapper for WebRTC logging. */
public class Logging {
  // Keep in sync with webrtc/base/logging.h:LoggingSeverity.
  public enum Severity {
    LS_SENSITIVE, LS_VERBOSE, LS_INFO, LS_WARNING, LS_ERROR,
  };

  public static void log(Severity severity, String tag, String message) {
    Log.i(tag, message);
  }

  public static void d(String tag, String message) {
    log(Severity.LS_INFO, tag, message);
  }

  public static void e(String tag, String message) {
    log(Severity.LS_ERROR, tag, message);
  }

  public static void w(String tag, String message) {
    log(Severity.LS_WARNING, tag, message);
  }

  public static void e(String tag, String message, Throwable e) {
    log(Severity.LS_ERROR, tag, message);
    log(Severity.LS_ERROR, tag, e.toString());
  }

  public static void w(String tag, String message, Throwable e) {
    log(Severity.LS_WARNING, tag, message);
    log(Severity.LS_WARNING, tag, e.toString());
  }

  public static void v(String tag, String message) {
    log(Severity.LS_VERBOSE, tag, message);
  }



}
