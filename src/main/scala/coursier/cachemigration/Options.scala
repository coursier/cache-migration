package coursier.cachemigration

import java.nio.file.{Path, Paths}

import caseapp._
import coursier.cache.shaded.io.github.soc.directories.ProjectDirectories

final case class Options(
  from: Option[String] = None,
  to: Option[String] = None,
  jvmFrom: Option[String] = None,
  jvmTo: Option[String] = None,
  @Name("n")
    dryRun: Boolean = false,
  oneByOne: Option[Boolean] = None,
  @Name("n")
    cleanUp: Boolean = false,
  @Name("v")
    verbose: Int @@ Counter = Tag.of(0),
) {
  private lazy val home = Option(System.getProperty("user.home"))
    .map(Paths.get(_).toAbsolutePath)
    .getOrElse(sys.error(s"Cannot get home directory"))
  lazy val fromPath: Path =
    from
      .map(Paths.get(_).toAbsolutePath)
      .getOrElse {
        home.resolve(".coursier/cache/v1")
      }
  def isDefaultFrom: Boolean =
    from.isEmpty
  lazy val toPath: Path =
    to
      .map(Paths.get(_).toAbsolutePath)
      .getOrElse {
        val cacheDir = Paths.get(ProjectDirectories.from(null, null, "Coursier").cacheDir)
        cacheDir.resolve("v1").toAbsolutePath
      }
  lazy val jvmFromPathOpt: Option[Path] =
    jvmFrom
      .map(Paths.get(_).toAbsolutePath)
      .orElse {
        if (isDefaultFrom)
          Some(home.resolve(".coursier/cache/jvm"))
        else
          None
      }
  lazy val jvmToPathOpt: Option[Path] =
    jvmTo
      .map(Paths.get(_).toAbsolutePath)
      .orElse {
        if (isDefaultFrom)
          Some {
            val cacheDir = Paths.get(ProjectDirectories.from(null, null, "Coursier").cacheDir)
            cacheDir.resolve("jvm").toAbsolutePath
          }
        else
          None
      }
  lazy val verbosity: Int =
    Tag.unwrap(verbose)
  lazy val outputPrefix =
    if (dryRun) "(DRY RUN) "
    else ""
}
