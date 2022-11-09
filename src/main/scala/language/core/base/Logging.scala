package language.core.base

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.MessageType.WARNING
import com.intellij.openapi.ui.MessageType.INFO
import com.intellij.openapi.ui.MessageType.ERROR

import scala.concurrent.Future

trait Logging {
  trait Default[T] {
    def value:T
  }

  object Default {
    def apply[T](x:T):Default[T] = new Default[T] { def value:T = x }
    implicit def any[T]:Default[T] = Default[T](null.asInstanceOf[T])
    implicit def unit:Default[Unit] = Default[Unit](())
    implicit def seq[T]:Default[Seq[T]] = Default(Seq[T]())
    implicit def list[T]:Default[List[T]] = Default(List[T]())
    implicit def option[T]:Default[Option[T]] = Default(None)
  }

  def default[T](implicit a:Default[T]):T = a.value
  def success[T](implicit default:Default[T]):Future[T]
  = Future.successful(default.value)

  private implicit def asNotificationType(tpe:MessageType):NotificationType = tpe match {
    case MessageType.INFO => NotificationType.INFORMATION
    case MessageType.WARNING => NotificationType.WARNING
    case MessageType.ERROR => NotificationType.ERROR
  }

  private val LogOnlyGroup = new NotificationGroup("CORE Log", NotificationDisplayType.NONE, false)
  // private val WarningGroup = new NotificationGroup("CORE Warning", NotificationDisplayType.NONE, true)
  private val BalloonGroup = new NotificationGroup("CORE Message", NotificationDisplayType.BALLOON, true)

  private def logEvent(
    project: Option[Project],
    message: String,
    messageType: MessageType,
    notification: (String, MessageType) => Notification
  ) = {
    log(project, message, messageType, notification)
  }

  private def balloonEvent(project: Option[Project], title: String, message: String, messageType: MessageType) = {
    log(project, message, messageType, BalloonGroup.createNotification(title, null, _, _))
  }

  private def log(
    project: Option[Project],
    message: String,
    messageType: MessageType,
    notification: (String, MessageType) => Notification
  ) = {
    System.err.println(s"${messageType}: ${project.map(_.getName).getOrElse("project")}: $message")

    project match {
      case Some(p) if !p.isDisposed && p.isOpen => notification(message, messageType).notify(p)
      case None                                 => notification(message, messageType).notify(null)
      case _                                    => ()
    }
  }

  def showMessage(project: Project, title: String, message: String): Unit = {
    balloonEvent(Some(project), title, message, WARNING)
  }

  def logInfo(project: Option[Project], message: String): Unit = {
    logEvent(project, message, INFO, LogOnlyGroup.createNotification)
  }

  def logError(message: String): Unit = {
    logError(None, message)
  }

  def logError(project: Project, message: String): Unit = {
    logError(Some(project), message)
  }

  def logError(project: Option[Project], message: String): Unit = {
    logEvent(project, message, ERROR, LogOnlyGroup.createNotification)
  }

  def logWarning(project: Option[Project], message: String): Unit = {
    logEvent(project, message, WARNING, LogOnlyGroup.createNotification)
  }

  def logFatal[T](project: Option[Project], title: String, message: String): T = {
    logError(project, s"$title: $message")
    throw new RuntimeException(s"$title: $message")
  }

  def info(message:String): Unit = {
    logInfo(None, message)
  }

  def info(project:Project, message:String): Unit = {
    logInfo(Some(project), message)
  }

  def warning(message:String): Unit = {
    logWarning(None, message)
  }

  def warning(project:Project, message:String): Unit = {
    logWarning(Some(project), message)
  }

  def fatal[T](title: String, message: String): T = {
    logFatal(None, title, message)
  }

  def fatal[T](project: Project, title: String, message: String): T = {
    logFatal(Some(project), title, message)
  }

  def exception[T](project: Project, title:String, ex:Throwable)(implicit default:Default[T]):T = {
    ex.printStackTrace()
    logError(project, ex.getMessage)
    default.value
  }
}
