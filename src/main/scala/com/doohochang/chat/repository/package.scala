package com.doohochang.chat

import java.sql.Timestamp

import slick.jdbc.{GetResult, SetParameter}

import entity._

package object repository {
  implicit val setUserID: SetParameter[User.ID] = SetParameter { (userID, pp) => pp.setString(userID.id) }
  implicit val setChatRoomID: SetParameter[ChatRoom.ID] = SetParameter { (chatRoomID, pp) => pp.setString(chatRoomID.id) }
  implicit val setSequenceNr: SetParameter[SequenceNr] = SetParameter { (sequenceNr, pp) => pp.setLong(sequenceNr.toLong) }
  implicit val setDateTime: SetParameter[DateTime] = SetParameter { (dateTime, pp) =>
    pp.setTimestamp(new Timestamp(dateTime.getMillis))
  }

  implicit val getUserID: GetResult[User.ID] = GetResult { r => User.ID(r.nextString()) }
  implicit val getChatRoomID: GetResult[ChatRoom.ID] = GetResult { r => ChatRoom.ID(r.nextString()) }
  implicit val getSequenceNr: GetResult[SequenceNr] = GetResult { r => SequenceNr(r.nextLong()) }
  implicit val getDateTime: GetResult[DateTime] = GetResult { r => new DateTime(r.nextTimestamp().getTime)}
}
