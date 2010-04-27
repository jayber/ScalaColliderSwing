/*
 *  ScalaColliderSwing.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2010 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.synth.swing

import java.awt.EventQueue

object ScalaColliderSwing extends Runnable {
   val name          = "ScalaCollider-Swing"
   val version       = 0.11
   val copyright     = "(C)opyright 2008-2010 Hanns Holger Rutz"
   def versionString = (version + 0.001).toString.substring( 0, 4 )

   def main( args: Array[ String ]) {
      EventQueue.invokeLater( this )
   }
                                                                                                                  
   def run {
      val s = new SwingServer( "Default" )
      s.options.programPath.value = "/Users/rutz/Documents/devel/fromSVN/SuperCollider3/common/build/scsynth"
      val sspw = new ServerStatusPanel( s ).makeWindow
      val ntp  = new NodeTreePanel( s )
      val sif  = new ScalaInterpreterFrame( s, ntp )
      sif.setLocation( sspw.getX + sspw.getWidth + 32, sif.getY )
      val ntpw = ntp.makeWindow
      ntpw.setLocation( sspw.getX, sspw.getY + sspw.getHeight + 32 )

      sspw.setVisible( true )
      ntpw.setVisible( true )
      sif.setVisible( true )
   }
}