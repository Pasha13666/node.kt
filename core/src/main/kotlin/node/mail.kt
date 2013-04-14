package node.mail

import javax.mail.Session
import java.util.Properties
import node.Configuration
import node.util.lazy
import javax.mail.internet.MimeMessage
import javax.mail.internet.InternetAddress
import javax.mail.Message.RecipientType.TO
import javax.mail.Transport
import node.util.with

private val session = lazy {
  val properties = Properties()

  // load generic properties
  with (Configuration.get("mail.properties")) {
    if (it is Map<*,*>) {
      it.entrySet().forEach {
        if (it.key is String && it.value is String) {
          properties.setProperty(it.key as String, it.value as String)
        }
      }
    }
  }

  // load address mappings
  val session = Session.getDefaultInstance(properties)!!
  Configuration.map("mail.protocols").entrySet().forEach {
    if (it.value is String) {
      session.setProtocolForAddress(it.key, it.value as String)
    }
  }
  session
}

/**
 * The simplest API for sending a message
 */
fun sendMail(to: String, from: String, subject: String, content: String) {
  val message = MimeMessage(session())
  message.setFrom(InternetAddress(from))
  message.addRecipient(TO, InternetAddress(to))
  message.setSubject(subject)
  message.setText(content)
  Transport.send(message)
}

/**
 * Send an HTML message
 */
fun sendHTMLMail(to: String, from: String, subject: String, html: String, text: String? = null) {
  val message = MimeMessage(session())
  message.setFrom(InternetAddress(from))
  message.addRecipient(TO, InternetAddress(to))
  message.setSubject(subject)
  if (text != null) message.setText(text)
  message.setContent(html, "text/html")
  Transport.send(message)
}