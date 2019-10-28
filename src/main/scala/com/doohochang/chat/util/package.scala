package com.doohochang.chat

package object util {
  implicit class ThrowableOps(e: Throwable) {
    def verbose: String =
      e.toString +
        e.getStackTrace.mkString(start = "\n  ", sep = "\n  ", end = "\n") +
        (
          if (e.getCause == null) ""
          else "Caused by " + e.getCause.verbose
        )
  }
}
