package com.doohochang.chat
package config

import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory

case class Configuration(
  grpcPort: Int,
  jwtSecretKey: String,
  accessTokenExpiry: Duration,
  refreshTokenExpiry: Duration
)

object Configuration {
  def load(): Configuration = {
    val config = ConfigFactory.load().getConfig("chat-server")

    Configuration(
      grpcPort = config.getInt("grpc-port"),
      jwtSecretKey = config.getString("jwt-secret-key"),
      accessTokenExpiry = Duration.standardMinutes(config.getLong("access-token-expiry-minutes")),
      refreshTokenExpiry = Duration.standardMinutes(config.getLong("refresh-token-expiry-minutes"))
    )
  }
}
