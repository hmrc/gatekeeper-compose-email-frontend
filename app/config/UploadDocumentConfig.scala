package config

import javax.inject.Inject
import javax.inject.Singleton
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.duration.{Duration, FiniteDuration}
import play.api.Configuration

@Singleton
class UploadDocumentsConfig @Inject() (servicesConfig: ServicesConfig, configuration: Configuration) {

  lazy val baseUrl: String =
    servicesConfig.baseUrl("upload-documents-frontend")

  lazy val callbackUrlPrefix: String =
    servicesConfig.getConfString("upload-documents-frontend.callback-url-prefix", "")

  lazy val contextPath: String =
    servicesConfig.getConfString("upload-documents-frontend.context-path", "/upload-documents")

  lazy val publicUrl: String =
    servicesConfig.getConfString("upload-documents-frontend.public-url", "")

  lazy val retryIntervals: Seq[FiniteDuration] =
    getConfIntervals("upload-documents-frontend", configuration)

  lazy val initializationUrl: String = s"$baseUrl$contextPath/initialize"

  lazy val wipeOutUrl: String = s"$baseUrl$contextPath/wipe-out"

  def getConfIntervals(serviceKey: String, configuration: Configuration): Seq[FiniteDuration] =
    configuration
      .getOptional[Seq[String]](s"microservice.services.$serviceKey.retryIntervals")
      .map(_.map(Duration.create).map(d => FiniteDuration(d.length, d.unit)))
      .getOrElse(Seq.empty)
}

