// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package org.example.streaming.Data

@SerialVersionUID(0L)
final case class LoginLog(
    srcIp: _root_.scala.Predef.String = "",
    dstIp: _root_.scala.Predef.String = "",
    username: _root_.scala.Predef.String = "",
    password: _root_.scala.Predef.String = "",
    loginTimestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp] = _root_.scala.None,
    recordedTimestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp] = _root_.scala.None
    ) extends scalapb.GeneratedMessage with scalapb.Message[LoginLog] with scalapb.lenses.Updatable[LoginLog] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = srcIp
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
      
      {
        val __value = dstIp
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
        }
      };
      
      {
        val __value = username
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, __value)
        }
      };
      
      {
        val __value = password
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, __value)
        }
      };
      if (loginTimestamp.isDefined) {
        val __value = loginTimestamp.get
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      };
      if (recordedTimestamp.isDefined) {
        val __value = recordedTimestamp.get
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      };
      __size
    }
    final override def serializedSize: _root_.scala.Int = {
      var read = __serializedSizeCachedValue
      if (read == 0) {
        read = __computeSerializedValue()
        __serializedSizeCachedValue = read
      }
      read
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      {
        val __v = srcIp
        if (__v != "") {
          _output__.writeString(1, __v)
        }
      };
      {
        val __v = dstIp
        if (__v != "") {
          _output__.writeString(2, __v)
        }
      };
      {
        val __v = username
        if (__v != "") {
          _output__.writeString(3, __v)
        }
      };
      {
        val __v = password
        if (__v != "") {
          _output__.writeString(4, __v)
        }
      };
      loginTimestamp.foreach { __v =>
        val __m = __v
        _output__.writeTag(5, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      recordedTimestamp.foreach { __v =>
        val __m = __v
        _output__.writeTag(6, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): org.example.streaming.Data.LoginLog = {
      var __srcIp = this.srcIp
      var __dstIp = this.dstIp
      var __username = this.username
      var __password = this.password
      var __loginTimestamp = this.loginTimestamp
      var __recordedTimestamp = this.recordedTimestamp
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __srcIp = _input__.readString()
          case 18 =>
            __dstIp = _input__.readString()
          case 26 =>
            __username = _input__.readString()
          case 34 =>
            __password = _input__.readString()
          case 42 =>
            __loginTimestamp = Option(_root_.scalapb.LiteParser.readMessage(_input__, __loginTimestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)))
          case 50 =>
            __recordedTimestamp = Option(_root_.scalapb.LiteParser.readMessage(_input__, __recordedTimestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)))
          case tag => _input__.skipField(tag)
        }
      }
      org.example.streaming.Data.LoginLog(
          srcIp = __srcIp,
          dstIp = __dstIp,
          username = __username,
          password = __password,
          loginTimestamp = __loginTimestamp,
          recordedTimestamp = __recordedTimestamp
      )
    }
    def withSrcIp(__v: _root_.scala.Predef.String): LoginLog = copy(srcIp = __v)
    def withDstIp(__v: _root_.scala.Predef.String): LoginLog = copy(dstIp = __v)
    def withUsername(__v: _root_.scala.Predef.String): LoginLog = copy(username = __v)
    def withPassword(__v: _root_.scala.Predef.String): LoginLog = copy(password = __v)
    def getLoginTimestamp: com.google.protobuf.timestamp.Timestamp = loginTimestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)
    def clearLoginTimestamp: LoginLog = copy(loginTimestamp = _root_.scala.None)
    def withLoginTimestamp(__v: com.google.protobuf.timestamp.Timestamp): LoginLog = copy(loginTimestamp = Option(__v))
    def getRecordedTimestamp: com.google.protobuf.timestamp.Timestamp = recordedTimestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)
    def clearRecordedTimestamp: LoginLog = copy(recordedTimestamp = _root_.scala.None)
    def withRecordedTimestamp(__v: com.google.protobuf.timestamp.Timestamp): LoginLog = copy(recordedTimestamp = Option(__v))
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = srcIp
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = dstIp
          if (__t != "") __t else null
        }
        case 3 => {
          val __t = username
          if (__t != "") __t else null
        }
        case 4 => {
          val __t = password
          if (__t != "") __t else null
        }
        case 5 => loginTimestamp.orNull
        case 6 => recordedTimestamp.orNull
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(srcIp)
        case 2 => _root_.scalapb.descriptors.PString(dstIp)
        case 3 => _root_.scalapb.descriptors.PString(username)
        case 4 => _root_.scalapb.descriptors.PString(password)
        case 5 => loginTimestamp.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        case 6 => recordedTimestamp.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = org.example.streaming.Data.LoginLog
}

object LoginLog extends scalapb.GeneratedMessageCompanion[org.example.streaming.Data.LoginLog] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[org.example.streaming.Data.LoginLog] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, _root_.scala.Any]): org.example.streaming.Data.LoginLog = {
    _root_.scala.Predef.require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    org.example.streaming.Data.LoginLog(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(2), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(3), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.get(__fields.get(4)).asInstanceOf[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]],
      __fieldsMap.get(__fields.get(5)).asInstanceOf[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[org.example.streaming.Data.LoginLog] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      org.example.streaming.Data.LoginLog(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]]),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]])
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = DataProto.javaDescriptor.getMessageTypes.get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = DataProto.scalaDescriptor.messages(0)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 5 => __out = com.google.protobuf.timestamp.Timestamp
      case 6 => __out = com.google.protobuf.timestamp.Timestamp
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = org.example.streaming.Data.LoginLog(
    srcIp = "",
    dstIp = "",
    username = "",
    password = "",
    loginTimestamp = _root_.scala.None,
    recordedTimestamp = _root_.scala.None
  )
  implicit class LoginLogLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, org.example.streaming.Data.LoginLog]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, org.example.streaming.Data.LoginLog](_l) {
    def srcIp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.srcIp)((c_, f_) => c_.copy(srcIp = f_))
    def dstIp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.dstIp)((c_, f_) => c_.copy(dstIp = f_))
    def username: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.username)((c_, f_) => c_.copy(username = f_))
    def password: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.password)((c_, f_) => c_.copy(password = f_))
    def loginTimestamp: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.timestamp.Timestamp] = field(_.getLoginTimestamp)((c_, f_) => c_.copy(loginTimestamp = Option(f_)))
    def optionalLoginTimestamp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]] = field(_.loginTimestamp)((c_, f_) => c_.copy(loginTimestamp = f_))
    def recordedTimestamp: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.timestamp.Timestamp] = field(_.getRecordedTimestamp)((c_, f_) => c_.copy(recordedTimestamp = Option(f_)))
    def optionalRecordedTimestamp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]] = field(_.recordedTimestamp)((c_, f_) => c_.copy(recordedTimestamp = f_))
  }
  final val SRCIP_FIELD_NUMBER = 1
  final val DSTIP_FIELD_NUMBER = 2
  final val USERNAME_FIELD_NUMBER = 3
  final val PASSWORD_FIELD_NUMBER = 4
  final val LOGINTIMESTAMP_FIELD_NUMBER = 5
  final val RECORDEDTIMESTAMP_FIELD_NUMBER = 6
  def of(
    srcIp: _root_.scala.Predef.String,
    dstIp: _root_.scala.Predef.String,
    username: _root_.scala.Predef.String,
    password: _root_.scala.Predef.String,
    loginTimestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp],
    recordedTimestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]
  ): _root_.org.example.streaming.Data.LoginLog = _root_.org.example.streaming.Data.LoginLog(
    srcIp,
    dstIp,
    username,
    password,
    loginTimestamp,
    recordedTimestamp
  )
}