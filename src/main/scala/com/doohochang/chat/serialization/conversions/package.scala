package com.doohochang.chat
package serialization

import com.github.nscala_time.time.Imports._

import conversion._

package object conversions extends ChatRoomConversions {
  implicit val dateTimeEncoder: Encoder[DateTime, Long] = _.getMillis
  implicit val dateTimeDecoder: Decoder[Long, DateTime] = new org.joda.time.DateTime(_).purelyDecoded
}
