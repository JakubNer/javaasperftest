package template.messaging

@SerialVersionUID(100L)
class NeedleCandidateMessage (val msgid: Int, val lines: Array[String], val hashes:Array[String]) extends Serializable

@SerialVersionUID(101L)
class BackchannelMessage (val foundLine: String) extends Serializable
