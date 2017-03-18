package node.modules.mail

import node.Configuration
import java.util.*
import javax.mail.Message.RecipientType.TO
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

private val session by lazy {
    val properties = Properties()

    // load generic properties
    (Configuration["mail.properties"] as? Map<*, *>)?.filter { it.key is String && it.value is String }
            ?.forEach { properties.setProperty(it.key as String, it.value as String) }

    // load address mappings
    val session = Session.getDefaultInstance(properties)!!
    Configuration.map("mail.protocols").filter { it.value is String }
            .forEach { session.setProtocolForAddress(it.key, it.value as String) }
    session
}

/**
 * The simplest API for sending a message
 */
fun sendMail(to: String, from: String, subject: String, content: String) {
    val message = MimeMessage(session)
    message.setFrom(InternetAddress(from))
    message.addRecipient(TO, InternetAddress(to))
    message.subject = subject
    message.setText(content)
    Transport.send(message)
}

/**
 * Send an HTML message
 */
fun sendHTMLMail(to: String, from: String, subject: String, html: String, text: String? = null) {
    val message = MimeMessage(session)
    message.setFrom(InternetAddress(from))
    message.addRecipient(TO, InternetAddress(to))
    message.subject = subject
    if (text != null) message.setText(text)
    message.setContent(html, "text/html")
    Transport.send(message)
}