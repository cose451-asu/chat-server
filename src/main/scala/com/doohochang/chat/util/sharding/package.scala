package com.doohochang.chat
package util

import akka.cluster.sharding.typed.scaladsl._

import entity._
import behavior._

package object sharding {
  implicit class ShardingOps(sharding: ClusterSharding) {
    def chatRoom(id: ChatRoom.ID): EntityRef[ChatRoomBehavior.Request] =
      sharding.entityRefFor(
        ChatRoomBehavior.entityTypeKey,
        id.id
      )
  }
}
