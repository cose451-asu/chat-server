package com.doohochang.chat
package service

import io.grpc.Context

import entity._

object ContextKeys {
  val userIDKey: Context.Key[User.ID] = Context.key("user-id")
}
