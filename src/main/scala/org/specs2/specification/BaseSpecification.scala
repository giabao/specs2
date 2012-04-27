package org.specs2
package specification

import org.specs2.internal.scalaz._
import Scalaz._
import main.Arguments
import Fragments._
import reflect.Classes._

/**
 * A Base specification contains the minimum elements for a Specification
 * 
 * - a Seq of Fragments, available through the SpecificationStructure trait
 * - methods for creating Fragments from the FragmentsBuilder trait
 * - methods to include other specifications
 *
 */
trait BaseSpecification extends SpecificationStructure with FragmentsBuilder with SpecificationInclusion

/**
 * additional methods to include other specifications or Fragments
 */
trait SpecificationInclusion { this: FragmentsBuilder =>
  def include(f: Fragments): FragmentsFragment = fragmentsFragments(f)
  implicit def include(s: SpecificationStructure): FragmentsFragment = include(s.content)
  def include(s: SpecificationStructure, ss: SpecificationStructure*): FragmentsFragment = include(ma((Seq(s)++ss).map(_.content)).sum)
  def include(args: Arguments, s: SpecificationStructure*): FragmentsFragment = include(ma(s.map(_.content)).sum.overrideArgs(args))
}
/**
 * The structure of a Specification is simply defined as a sequence of fragments
 */
trait SpecificationStructure { 
  /** declaration of Fragments from the user */
  def is: Fragments
  /** this method can be overriden to map additional behavior in the user-defined fragments */
  def map(fs: =>Fragments): Fragments = fs
  /** 
   * this "cached" version of the Fragments is kept hidden from the user to avoid polluting
   * the Specification namespace.
   * SpecStart and SpecEnd fragments are added if the user haven't inserted any
   */
  private[specs2] lazy val content: Fragments = Fragments.withSpecName(map(is), this)
}

/**
 * methods for creating SpecificationStructure instances from fragments
 */
private[specs2]
object SpecificationStructure {
  import collection.Iterablex._
  
  def apply(fs: Fragments): SpecificationStructure = new SpecificationStructure {
    def is = fs.fragments match {
      case SpecStart(n,a,l,so) +: middle :+ SpecEnd(_) => Fragments(Some(n), middle, a, l, so)
      case other                                       => fs
    }
  }
  def apply(fs: Seq[Fragment]): SpecificationStructure = apply(Fragments.create(fs:_*))

  def apply(spec: SpecificationStructure, arguments: Arguments): SpecificationStructure = apply(spec.content add arguments)

  /**
   * create a SpecificationStructure from a className, throwing an Error if that's not possible
   */
  def createSpecification(className: String, classLoader: ClassLoader = Thread.currentThread.getContextClassLoader)
                         (implicit args: Arguments = Arguments()): SpecificationStructure = {
    createSpecificationOption(className, classLoader) match {
      case Some(s) => s
      case None    => sys.error("can not create specification: "+className)
    }
  }

  /**
   * create a SpecificationStructure from a className, returning None if that's not possible
   */
  def createSpecificationOption(className: String, classLoader: ClassLoader = Thread.currentThread.getContextClassLoader)
                               (implicit args: Arguments = Arguments()) : Option[SpecificationStructure] = {
      // finally retry the original class name to display the error messages
    createSpecificationFromClassOrObject(className, classLoader).
      orElse(tryToCreateObject[SpecificationStructure](className, loader = classLoader))

  }

  /**
   * create a SpecificationStructure from a className, returning an Exception if that's not possible
   */
  def createSpecificationEither(className: String, classLoader: ClassLoader = Thread.currentThread.getContextClassLoader)
                               (implicit args: Arguments = Arguments()) : Either[Throwable, SpecificationStructure] = {
    // try to create the specification from a class name, without displaying possible errors
    createSpecificationFromClassOrObject(className, classLoader).map(Right(_)).
      // try to create the specification from an object class name
      getOrElse(tryToCreateObjectEither[SpecificationStructure](className, classLoader, Some(args)))
  }

  private def createSpecificationFromClassOrObject(className: String,
                                                   classLoader: ClassLoader = Thread.currentThread.getContextClassLoader)
                                                  (implicit args: Arguments = Arguments()) : Option[SpecificationStructure] = {
    // try to create the specification from a class name, without displaying possible errors
    tryToCreateObject[SpecificationStructure](className,
      printMessage = false,
      printStackTrace = false,
      loader = classLoader,
      parameter = Some(args)).
      // try to create the specification from an object class name
      orElse(tryToCreateObject[SpecificationStructure](className+"$",
      printMessage = false,
      printStackTrace = false,
      loader = classLoader,
      parameter = Some(args)))
  }

}