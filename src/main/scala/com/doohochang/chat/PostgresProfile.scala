package com.doohochang.chat

import com.github.tminglei.slickpg._
import slick.basic.Capability
import slick.jdbc.{JdbcCapabilities, SetParameter}

trait MyPostgresProfile
  extends ExPostgresProfile
  with PgArraySupport
  with PgRangeSupport
  with PgHStoreSupport
  with PgNetSupport
  with PgLTreeSupport {
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api = MyAPI

  object MyAPI extends API
    with ArrayImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits {
    implicit val strListTypeMapper: DriverJdbcType[List[String]] =
      new SimpleArrayJdbcType[String]("text").to(_.toList)

    implicit val setBytes: SetParameter[Array[Byte]] =
      SetParameter { (bytes, pp) =>
        pp.setBytes(bytes)
      }
  }
}

object PostgresProfile extends MyPostgresProfile
