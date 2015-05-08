package com.timushev.sbt.updates

import com.timushev.sbt.updates.versions._
import sbt.{IvySbt, Logger, ModuleID}

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object UpdatesFinder {

  import scala.Ordered._

  def findUpdates(ivy: IvySbt, log: Logger, allowPreRelease: Boolean)(module: ModuleID): Future[SortedSet[Version]] = {
    val current = Version(module.revision)
    val versions = Future {
      SortedSet(
        ivy.withIvy(log)(_.listRevisions(module.organization, module.name))
          .map(Version.apply)
          .toSeq: _*
      )
    }
    versions map (_ filter isUpdate(current) filterNot lessStable(current, allowPreRelease))
  }

  private def lessStable(current: Version, allowPreRelease: Boolean)(another: Version): Boolean = (current, another) match {
    case (ReleaseVersion(_), ReleaseVersion(_)) => false
    case (SnapshotVersion(_, _, _), _) => false
    case (_, SnapshotVersion(_, _, _)) => true
    case (ReleaseVersion(_), PreReleaseVersion(_, _)) => !allowPreRelease
    case (ReleaseVersion(_), PreReleaseBuildVersion(_, _, _)) => !allowPreRelease
    case (ReleaseVersion(_), _) => true
    case (_, _) => false
  }

  private def isUpdate(current: Version) = current < _

}
