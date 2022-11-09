-- This file is part of Hoppy.
--
-- Copyright 2015-2019 Bryan Gardiner <bog@khumba.net>
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.

{-# OPTIONS_GHC -fno-warn-unused-imports #-}

-- | The Hoppy User's Guide
module Foreign.Hoppy.Documentation.UsersGuide (
  -- * Overview
  -- $overview

  -- * Getting started
  -- $getting-started

  -- ** Project setup
  -- $getting-started-project-setup

  -- ** A first binding
  -- $getting-started-a-first-binding

  -- ** Types
  -- $getting-started-types

  -- ** Wrapping up the string binding
  -- $getting-started-wrapping-up-the-string-binding

  -- ** Functions
  -- $getting-started-functions

  -- ** Objects
  -- $getting-started-objects

  -- *** Generated bindings
  -- $getting-started-objects-generated-bindings

  -- *** Passing and returning objects
  -- $getting-started-objects-passing-and-returning-objects

  -- *** Garbage collection
  -- $getting-started-objects-garbage-collection

  -- *** Conversions
  -- $getting-started-objects-conversions

  -- ** API versioning
  -- $getting-started-api-versioning

  -- * Generators
  -- $generators

  -- ** C++
  -- $generators-cpp

  -- *** Module structure
  -- $generators-cpp-module-structure

  -- *** Object passing
  -- $generators-cpp-object-passing

  -- *** Callbacks
  -- $generators-cpp-callbacks

  -- ** Haskell
  -- $generators-hs

  -- *** Module structure
  -- $generators-hs-module-structure

  -- **** Variable exports
  -- $generators-hs-module-structure-variables

  -- **** Enum exports
  -- $generators-hs-module-structure-enums

  -- **** Function exports
  -- $generators-hs-module-structure-functions

  -- **** Callback exports
  -- $generators-hs-module-structure-callbacks

  -- **** Class exports
  -- $generators-hs-module-structure-classes

  -- *** Module dependencies
  -- $generators-hs-module-dependencies

  -- *** Object passing
  -- $generators-hs-object-passing

  -- *** Exceptions
  -- $generators-hs-exceptions
  ) where

import Data.Bits (Bits)
import Foreign.C (CInt, peekCString, withCString)
import Foreign.Hoppy.Generator.Language.Haskell
import Foreign.Hoppy.Generator.Main
import Foreign.Hoppy.Generator.Spec
import Foreign.Hoppy.Generator.Types
import Foreign.Hoppy.Generator.Version
import Foreign.Hoppy.Runtime
import Foreign.Ptr (FunPtr, Ptr)
import Language.Haskell.Syntax (
  HsName (HsIdent),
  HsQName (UnQual),
  HsType (HsTyCon),
  )
import System.IO.Unsafe (unsafePerformIO)

{- $overview

Hoppy is a foreign function interface (FFI) generator for interfacing Haskell
with C++.  It lets developers specify C++ interfaces in pure Haskell, and
generates code to expose that functionality to Haskell.  Hoppy is made up of a
few different packages that provide interface definition data structures and
code generators, some runtime support for Haskell bindings, and interface
definitions for the C++ standard library.

Bindings using Hoppy have three Cabal packages:

- A Haskell generator program (in @\/myproject-generator@) that knows the
interface definition and generates code for the next two parts.

- A C++ library (in @\/myproject-cpp@) that gets compiled into a shared object containing
the C++ half of the bindings.

- A Haskell library (in @\/myproject@) that links against the C++ library and
exposes the bindings.

The path names are suggested subdirectories of a project, and are used in this
document, but are not required.  Only the latter two items need to be packaged
and distributed to users of the binding (plus Hoppy itself which is a dependency
of the generated bindings).

-}
{- $getting-started

This section provides a gentle introduction to working with Hoppy.

-}
{- $getting-started-project-setup

To set up a new Hoppy project, it's recommended to start with the project in the
@example\/@ directory as a base.  It is a minimal project that defines a C++
function to reverse a @std::string@, exposes that to Haskell via a library, and
provides a demo program that uses the library.  The @example\/install.sh@ script
simply compiles and installs the generator, C++, and Haskell packages in turn.

The generator package specifies the C++ interface to be exposed, using the
functions and data types described in the rest of this section.

The C++ package is a mostly empty, primarily containing a @Setup.hs@ file that
invokes Hoppy build hooks, and the C++ code we're binding to.  When building
this package, Hoppy generates some C++ code and then relies on a Makefile we
provide for linking it together with any code we provided (see
@example\/example-cpp\/cpp\/Makefile@).  If you are relying on a system library,
you can link to it in the Makefile.

The Haskell package is even more empty than the C++ one.  It contains a similar
@Setup.hs@ to invoke Hoppy.  Nothing else is included in the package's library,
although you are free to add your own Haskell modules.  The executable ties
everything together by calling the C++ code.  It reverses the characters of each
input line it sees.

To publish this project, one would upload all three packages to Hackage.  (But
make sure to rename it first!)
-}

{-
$getting-started-a-first-binding

A complete C++ API is specified using Haskell data structures in
"Foreign.Hoppy.Generator.Spec".  At the top level is the 'Interface' type.  An
interface contains 'Module's which correspond to portions of functionality of
their interface; that is, collections of classes, functions, files, etc.
Functionality can be grouped arbitrarily into modules and doesn't have to follow
the structure of existing C++ files.  Modules contain 'Export's which refer to
concrete bound entities ('Function's, 'Class'es, etc.).

For starters, we will look at a single class.  Let's write a binding for
@std::string@.  An initial version could start as follows.

@
import "Foreign.Hoppy.Generator.Spec"

c_string :: 'Class'
c_string =
  'addReqIncludes' ['includeStd' \"string\"] $
  'makeClass' ('ident1' \"std\" \"string\") (Just $ 'toExtName' \"StdString\")
  []
  [ 'mkCtor' \"new\" []
  , 'mkConstMethod' \"at\" ['intT'] 'charT'
  , 'mkConstMethod' \"string\" [] 'sizeT'
  ]
@

There is quite a bit to look at here, so let's work through it.

First, everything that can be exported has two names besides the name used for
the Haskell binding (@c_string@ above).  'Identifier's are used to specify the
qualified C++ names of exports, including namespaces and template arguments.
For this example, our identifier is @std::string@, which we specify with the
'ident1' call above.  The number indicates the number of leading namespace
components.  'ident' can be used for top-level entities.

Exported entities also each have an /external name/ that uniquely identifies it
within an interface.  This name can be different from the name of the C++ entity
the export is referring to.  An external name is munged by the code generators
and must be a valid identifier in all languages a set of bindings will use, so
it is restricted to characters in the range @[a-zA-Z0-9_]@, and must start with
an alphabetic character.  Character case in external names will be preserved as
much as possible in generated code, although case conversions are sometimes
necessary (e.g. Haskell requiring identifiers to begin with upper or lower case
characters).  In the example, the 'toExtName' call specifies an explicit
external name for the class.  @Nothing@ may be provided to automatically derive
an external name from the given identifier.  The derived name is based on the
last component of the identifier, which in this case is just @string@.
Converting this to a Haskell type name gives @String@, which collides with the
built-in string type, so we give an explicit external name instead.

The third argument to 'makeClass' is a list of superclasses.  @std::string@ does
not derive from any classes so we leave this empty in the example.  When
specifying interfaces in Hoppy, only publicly accessible components need to (and
in fact, can) be referenced by Hoppy: public base classes, public methods and
variables, but never protected or private entities.

The final argument to 'makeClass' is a list of entities within the class.  Here
we can specify constructors, methods, and variables that the class exposes, via
the 'ClassEntity' type.  There are a few sets of methods for building class
entities:

- Basic forms: 'makeCtor', 'makeMethod', and 'makeClassVariable' are the core
functions for building class entities.  These are fully general, and take
parameters both for the C++ and external names, as well as staticness,
constness, etc.  There is also 'makeFnMethod' for defining a method that is
actually backed by a C++ function, not a method of the class; this can be used
when manual wrapping of a method is required, to wrap a method with a function
but make it look like a method.

- Convenience forms: 'mkCtor', 'mkMethod', 'mkConstMethod', 'mkStaticMethod',
'mkProp', 'mkBoolIsProp', 'mkBoolHasProp', 'mkStaticProp', and 'mkClassVariable'
only take the C++ name, and derive the external name from it, as well as
assuming other parameters (staticness, etc.).  These are what you typically use,
unless you need overloading, in which case use the overloading forms below.

- Overloading forms: 'mkMethod'', 'mkConstMethod'', and 'mkStaticMethod'' are
convenience functions for overloaded methods.  Overloading is handled by
defining multiple exports all pointing to a single C++ entity.  These in turn
become separate Haskell functions.  Because external names must be unique
though, a different external name must be provided for each overloaded form;
this is the second argument to these functions.

- Unwrapped forms: Underscore forms for all of the above are provided as well
(e.g. 'mkMethod_' and 'mkMethod'_') that return the actual object they create
('Method', 'Ctor', 'ClassVariable') instead of wrapping the result in a
'ClassEntity' as is usually desired.

Generated C++ bindings for exported entities usually need @#include@s in order
to access those entities.  This is done with 'Include' and 'Reqs' types.  When
defining bindings, all exportable types have an instance of the 'HasReqs'
typeclass, and 'addReqIncludes' can be used to add includes.  'includeStd'
produces @#include \<...>@ statements, and 'includeLocal' produces @#include
\"...\"@.

This use of 'addReqIncludes' also indicates a common pattern for writing class
bindings.  After constructing a 'Class' with 'makeClass', there are a number of
functions that modify the class definition in various ways.  These functions'
types always end in @... -> 'Class' -> 'Class'@, so that they can be chained
easily.  Among others, these functions include:

- 'addReqIncludes' to add needed C++ @#include@ statements.
- 'classAddEntities' to add additional entities to a class.
- 'classAddFeatures' to add common functionality to a class.
- 'classMakeException' to add exception support for a class.
- 'classSetConversion' to configure an implicit conversion.

The main point to using all of these functions is to chain them on the result of
'makeClass', but to bind the Haskell binding to final resulting value.  For
instance, if we have the following class:

@
c_NotFoundException :: 'Class'
c_NotFoundException =
  'addReqIncludes' ['includeStd' \"exceptions.hpp\"] $
  'classMakeException' $
  'makeClass' ('ident' \"NotFoundException\") Nothing []
  [ 'mkCtor' \"newCopy\" ['objT' c_NotFoundException]
  ]
@

Then, 'addReqIncludes' and 'classMakeException' modify the class object, and the
constructor definition makes use of the resulting object.  This works as
intended.  In some cases the order of modifiers is important -- for example,
marking a class as an exception class requires that there be a copy constructor
defined beforehand -- but usually order of modification does not matter.

Another point of note is the @c_@ prefix used on these two classes.  A suggested
naming convention for entities is:

- @v_@ for variables ('Variable').
- @f_@ for functions ('Function').
- @c_@ for classes ('Class').
- @e_@ for enums ('CppEnum').
- @cb_@ for callbacks ('Callback').
- @mod_@ for modules ('Module').

Hoppy follows this convention, but you are not required to in your own bindings.
It enables the use of proper casing on the actual entity name, and avoids
collision with existing Haskell names.

Given all this, we can improve our @std::string@ binding.  The @at()@ method
provides a non-const overload that returns a reference to a requested character.
Let's have two versions of @at()@, as well as expose the fact that @std::string@
is assignable, comparable, copyable, and equatable, with a second version:

@
c_string :: 'Class'
c_string =
  'addReqIncludes' ['includeStd' \"string\"] $
  'classAddFeatures' ['Assignable', 'Comparable', 'Foreign.Hoppy.Generator.Spec.ClassFeature.Copyable', 'Equatable'] $
  'makeClass' ('ident1' \"std\" \"string\") (Just $ 'toExtName' \"StdString\")
  []
  [ 'mkCtor' \"new\" []
  , 'mkConstMethod'' \"at\" \"at\" ['intT'] $ 'refT' 'charT'
  , 'mkConstMethod'' \"at\" \"get\" ['intT'] 'charT'
  , 'mkConstMethod' \"string\" [] 'sizeT'
  ]
@

-}