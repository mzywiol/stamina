package stamina
package testkit

import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.util._

trait StaminaTestKit { self: org.scalatest.WordSpecLike ⇒

  val defaultSampleId = "default"
  case class PersistableSample[FromVersion <: Version: VersionInfo](sampleId: String, persistable: AnyRef, description: Option[String]) {
    override def toString = SimpleClassNameExtractor(persistable.getClass) + description.map(" " + _).getOrElse("")
    val fromVersionNumber = implicitly[VersionInfo[FromVersion]].versionNumber

    def from[NewFromVersion <: Version: VersionInfo] = PersistableSample[NewFromVersion](sampleId, persistable, description)
  }

  def sample(persistable: AnyRef) = new PersistableSample[V1](defaultSampleId, persistable, None)
  def sample(sampleId: String, persistable: AnyRef) = new PersistableSample[V1](sampleId, persistable, Some(sampleId))
  def sample(sampleId: String, persistable: AnyRef, description: String) = new PersistableSample[V1](sampleId, persistable, Some(description))

  implicit class TestablePersisters(persisters: Persisters) extends org.scalatest.Matchers {
    def generateTestsFor(samples: PersistableSample[_]*): Unit = {
      samples.foreach { sample ⇒
        generateRoundtripTestFor(sample)
        generateStoredVersionsDeserializationTestsFor(sample)
      }
      // Here we could verify test coverage
    }

    private def generateRoundtripTestFor(sample: PersistableSample[_]) = {
      s"persist and unpersist $sample" in {
        persisters.unpersist(persisters.persist(sample.persistable)) should equal(sample.persistable)
      }
    }

    private def generateStoredVersionsDeserializationTestsFor(sample: PersistableSample[_]): Unit = {
      val serialized: Option[Persisted] = Try(persisters.persist(sample.persistable)).toOption
      serialized.flatMap(latestDeserializableVersion).map(latestVersion ⇒
        Range.inclusive(sample.fromVersionNumber, latestVersion).foreach { version ⇒
          s"deserialize the stored serialized form of $sample version $version" in {
            verifyByteStringDeserialization(sample, version, latestVersion, serialized.get)
          }
        })
    }

    def latestDeserializableVersion(persisted: Persisted) =
      Try(Range(persisted.version, V50.Info.versionNumber).filter(v ⇒ persisters.canUnpersist(persisted.copy(version = v))).max).toOption

    private def verifyByteStringDeserialization(sample: PersistableSample[_], version: Int, latestVersion: Int, serialized: Persisted): Unit = {
      byteStringFromResource(serialized.key, version, sample.sampleId) match {
        case Success(binary) ⇒
          persisters.unpersist(binary) should equal(sample.persistable)
        case Failure(_: java.io.FileNotFoundException) if version == latestVersion ⇒
          val writtenToPath = saveByteArrayToTargetSerializationDirectory(serialized.bytes.toArray, serialized.key, version, sample.sampleId)
          fail(s"You appear to have added a new serialization sample to the stamina persisters' test.\n" +
            "A serialized version of this sample must be stored as a project resource for future reference, to ensure future versions of the software can still correctly deserialize serialized objects in this format.\n" +
            "Please copy the generated serialized data into the project test resources:\n" +
            s"  cp $writtenToPath $$PROJECT_PATH/src/test/resources/$serializedObjectsPackage")

        case Failure(_: java.io.FileNotFoundException) if version < latestVersion ⇒
          fail(s"While testing that the older serialized version $version of sample with key ${serialized.key} and sample id ${sample.sampleId} was not found")
        case Failure(other) ⇒
          fail(s"Failure while decoding serialized version $version of sample with key ${serialized.key} and sample id ${sample.sampleId} was not found", other)
      }
    }

    private def decodeBase64(s: String): Try[Array[Byte]] = Try(Base64.getDecoder.decode(s.getBytes(StandardCharsets.UTF_8)))

    private def encodeBase64(bytes: Array[Byte]): Array[Byte] = Base64.getEncoder.encode(bytes)

    private def byteStringFromResource(key: String, version: Int, sampleId: String): Try[Persisted] = {
      import scala.io.Source
      val resourceName = s"/$serializedObjectsPackage/${filename(key, version, sampleId)}"

      Option(this.getClass.getResourceAsStream(resourceName))
        .map(Success(_)).getOrElse(Failure(new java.io.FileNotFoundException(resourceName)))
        .map(Source.fromInputStream(_).mkString)
        .flatMap(decodeBase64)
        .map(akka.util.ByteString(_))
        .map(Persisted(key, version, _))
    }

    private def saveByteArrayToTargetSerializationDirectory(bytes: Array[Byte], key: String, version: Int, sampleId: String) = {
      import java.nio.file._
      val path = Paths.get(targetDirectoryForExampleSerializations, filename(key, version, sampleId))
      Files.write(path, encodeBase64(bytes), StandardOpenOption.TRUNCATE_EXISTING)
      path.toAbsolutePath
    }

    private def filename(key: String, version: Int, sampleId: String) = s"$key-v$version-" + sampleId.replaceAll("\\s+", "-")

    val targetDirectoryForExampleSerializations = System.getProperty("java.io.tmpdir")
    val serializedObjectsPackage = "serialization"
  }

  // Workaround for https://issues.scala-lang.org/browse/SI-2034
  private[testkit] object SimpleClassNameExtractor {
    def extractSimpleClassName(input: String) = input.split("\\.").last.split("\\$").last
    def apply[T](input: Class[T]): String = extractSimpleClassName(input.getName)
  }
}
