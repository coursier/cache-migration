package coursier.cachemigration

import java.io.IOException
import java.nio.file._
import java.util.stream.{Stream => JavaStream}

import caseapp._
import coursier.cache.CacheLocks

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object CacheMigration extends CaseApp[Options] {

  private def associatedFiles(file: Path): Seq[Path] = {
    val dir = file.getParent
    val name = file.getFileName.toString

    val ttlFile = dir.resolve(s".$name.checked")
    val errFile = dir.resolve(s".$name.error")

    val auxFiles = {
      val auxPrefix = s".${name}__"
      var stream: JavaStream[Path] = null
      try {
        stream = Files.list(dir)
        stream
          .iterator
          .asScala
          .filter(_.getFileName.toString.startsWith(auxPrefix))
          .filter(Files.isRegularFile(_))
          .toVector
      } finally {
        if (stream != null)
          stream.close()
      }
    }

    Seq(ttlFile, errFile).filter(Files.isRegularFile(_)) ++ auxFiles
  }

  private def isStandardFile(path: Path): Boolean =
    Files.isRegularFile(path) && {
      val name = path.getFileName.toString
      !name.startsWith(".") || name == ".directory"
    }

  private def countMissingInDest(options: Options, files: Iterator[Path]): Int =
    files.count { path =>
      val relPath = options.fromPath.relativize(path)
      val dest = options.toPath.resolve(relPath)
      !Files.exists(dest)
    }

  @tailrec
  private def cleanUpDir(dir: Path): Unit = {
    val deleted =
      try Files.deleteIfExists(dir)
      catch {
        case _: IOException =>
          false
      }
    if (deleted)
      cleanUpDir(dir.getParent.normalize)
  }

  private def process(options: Options, files: Iterator[Path]): Unit =
    files.foreach { path =>
      val relPath = options.fromPath.relativize(path)
      val associatedFiles0 = associatedFiles(path)

      val dest = options.toPath.resolve(relPath)
      if (Files.exists(dest)) {
        if (options.verbosity >= 1)
          System.err.println(s"$relPath already exists in ${options.toPath}, ignoring it")
        if (options.cleanUp && !options.dryRun) {
          for (f <- path +: associatedFiles0) {
            Files.deleteIfExists(f)
            cleanUpDir(f.getParent.normalize)
          }
        }
      } else if (!options.dryRun) {
        def doMove(): Unit = {
          for (f <- path +: associatedFiles0) {
            val relPath = options.fromPath.relativize(f)
            Files.move(f, options.toPath.resolve(relPath), StandardCopyOption.REPLACE_EXISTING)
          }
        }
        val moved = CacheLocks.withLockOr(options.toPath.toFile, dest.toFile)({ doMove(); true }, Some(false))
        if (!moved)
          System.err.println(s"Could not move $relPath (locked), try again.")
      }
    }

  private def walk[T](dir: Path, f: Iterator[Path] => T): T = {

    def helper(path: Path): Stream[Path] =
      if (!Files.exists(path)) Stream.empty
      else if (Files.isDirectory(path)) {
        var stream: JavaStream[Path] = null
        val elems =
          try {
            stream = Files.list(path)
            stream.iterator.asScala.toVector
          } finally {
            if (stream != null)
              stream.close()
          }
        elems.toStream.flatMap(helper) #::: Stream(path)
      }
      else Stream(path)

    f(helper(dir).iterator)
  }

  private def oneByOneMove(options: Options): Unit = {
    if (options.verbosity >= 0)
      System.err.println(s"Moving files individually from ${options.fromPath} to ${options.toPath}")

    var stream: JavaStream[Path] = null

    val count0 = walk(options.fromPath, it => countMissingInDest(options, it.filter(isStandardFile)))
    System.err.println(s"Found $count0 files to move")

    walk(options.fromPath, it => process(options, it.filter(isStandardFile)))
    if (options.cleanUp && !options.dryRun)
      walk(options.fromPath, it => it.filter(Files.isDirectory(_)).foreach(cleanUpDir(_)))
  }

  private def simpleMove(options: Options): Boolean =
    !Files.exists(options.toPath) && {
      if (options.verbosity >= 0)
        System.err.println(s"${options.outputPrefix}Moving ${options.fromPath} to ${options.toPath}")
      if (!options.dryRun) {
        Files.createDirectories(options.toPath.getParent.normalize)
        try Files.move(options.fromPath, options.toPath, StandardCopyOption.ATOMIC_MOVE)
        catch {
          case _: AtomicMoveNotSupportedException =>
            Files.move(options.fromPath, options.toPath)
        }
      }
      true
    }

  private def simpleJvmMove(options: Options): Option[Boolean] =
    for {
      from <- options.jvmFromPathOpt
      if Files.exists(from)
      to <- options.jvmToPathOpt
    } yield {
      !Files.exists(to) && {
        if (options.verbosity >= 0)
          System.err.println(s"${options.outputPrefix}Moving $from to $to")
        if (!options.dryRun) {
          Files.createDirectories(to.getParent.normalize)
          try Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
          catch {
            case _: AtomicMoveNotSupportedException =>
              Files.move(from, to)
          }
        }
        true
      }
    }

  def run(options: Options, args: RemainingArgs): Unit = {

    if (args.all.nonEmpty) {
      System.err.println(s"Unexpected arguments: ${args.all.map(arg => s"'$arg'").mkString(", ")}")
      sys.exit(1)
    }

    if (!Files.exists(options.fromPath)) {
      System.err.println(s"${options.fromPath} not found, exiting.")
      sys.exit(if (options.isDefaultFrom) 0 else 1)
    } else if (!Files.isDirectory(options.fromPath)) {
      System.err.println(s"${options.fromPath} is not a directory, exiting.")
      sys.exit(1)
    }

    (options.jvmFromPathOpt, options.jvmToPathOpt) match {
      case (Some(f), None) =>
        System.err.println(s"--jvm-to needs to be specified along with --jvm-from")
        sys.exit(1)
      case (None, Some(t)) =>
        System.err.println(s"--jvm-from needs to be specified along with --jvm-to")
        sys.exit(1)
      case _ =>
    }

    simpleJvmMove(options) match {
      case Some(false) =>
        System.err.println(
          s"Warning: could not move ${options.jvmFromPathOpt.get} to ${options.jvmToPathOpt.get}, as ${options.jvmToPathOpt} already exists"
        )
      case _ =>
    }

    if (!simpleMove(options))
      options.oneByOne match {
        case Some(true) =>
          oneByOneMove(options)
        case None =>
          System.err.println(
           s"""Both
              |  ${options.fromPath}
              |and
              |  ${options.toPath}
              |exist.
              |
              |Pass --one-by-one to move the elements of
              |  ${options.fromPath}
              |to
              |  ${options.toPath}
              |
              |Pass --clean-up to also remove empty directories in
              |  ${options.fromPath}
              |(including itself if it ends up empty).
              |
              |Alternatively, remove
              |  ${options.fromPath}
              |if you think it can be discarded.""".stripMargin
          )
        case Some(false) =>
          System.err.println(
           s"""Both
              |  ${options.fromPath}
              |and
              |  ${options.toPath}
              |exist.""".stripMargin
          )
      }
  }
}
