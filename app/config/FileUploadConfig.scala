package config

import configs.ConfigReader
import play.api.Configuration
import uk.gov.hmrc.crypto.TypesafeConfigOps

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploadConfig @Inject() (config: Configuration) {

  def readUpscanInitServiceProtocol: String =
    getUpscanInitiateConfig[String]("protocol")

  def readUpscanInitServiceHost: String =
    getUpscanInitiateConfig[String]("host")

  def readUpscanInitServicePort: String =
    getUpscanInitiateConfig[String]("port")

  def readMaxFileSize(uploadDocumentKey: String): Long =
    getUpscanInitiateConfig[Long](s"$uploadDocumentKey.max-file-size")

  def readMaxFileSizeHumanReadable(uploadDocumentKey: String): String =
    s"${getUpscanInitiateConfig[Long](s"$uploadDocumentKey.max-file-size") / (1024 * 1024)}MB"

  def readMaxUploadsValue(uploadDocumentKey: String): Int =
    getUpscanInitiateConfig[Int](s"$uploadDocumentKey.max-uploads")

  private def getUpscanInitiateConfig[A : ConfigReader](key: String): A =
    config.underlying
      .get[A](s"microservice.services.upscan-initiate.$key")
      .value
}
