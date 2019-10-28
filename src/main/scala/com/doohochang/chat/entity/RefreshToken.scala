package com.doohochang.chat
package entity

case class RefreshToken(
  userID: User.ID,
  token: String,
  expiredAt: DateTime
)
