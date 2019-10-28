package com.doohochang.chat.entity

case class User(
  id: User.ID,
  name: String
)

object User extends ID[String]
