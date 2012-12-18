/*
 *  GUI.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2012 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.synth
package swing

import sys.error
import de.sciss.gui.PeakMeter
import de.sciss.osc.Message
import de.sciss.synth.{GraphFunction => SGraphFunction, Group => SGroup, Server => SServer, Node => SNode,
   AudioBus => SAudioBus, Synth => SSynth, osc => sosc, SynthDef => SSynthDef, SynthGraph => SSynthGraph }
import scala.swing.{Swing, BoxPanel, Orientation, Frame}
import de.sciss.{synth, osc}
import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}
import java.io.File
import de.sciss.gui.j.WavePainter
import javax.swing.JComponent
import java.awt.{RenderingHints, Color, Dimension, Rectangle, Graphics2D, Graphics}

object GUI {
   final class Factory[ T ] private[swing] ( target: => T ) { def gui: T = target }

   final class Group private[swing] ( val group: SGroup ) {
      def tree() { error( "TODO" )}
   }

   final class AudioBus private[swing] ( val bus: SAudioBus ) {
      def meter( target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail ) : Frame = {
         makeAudioBusMeter( bus.server, bus.toString(), AudioBusMeterConfig( bus, target, addAction ) :: Nil )
      }
   }

   private final case class GUIRecordOut( in: GE )( chanFun: Int => Unit )
   extends UGenSource.ZeroOut( "GUIRecordOut" ) with WritesBus {
      protected def makeUGens { unwrap( in.expand.outputs )}

      protected def makeUGen( ins: IIdxSeq[ UGenIn ]) {
         if( ins.isEmpty ) return

         import synth._
         import ugen._
         val rate       = ins.map( _.rate ).max
         val signal: GE = if( rate == audio ) ins else K2A.ar( ins )
         val buf        = "$buf".ir
//         val dur        = "$dur".ir
//         val out        = Out.ar( bus, signal )

         // XXX RecordBuf doneAction is broken for multi-channel signals
         /* val out = */ RecordBuf.ar( signal, buf, loop = 0 /* , doneAction = freeSelf */ )
//         val out = DiskOut.ar( buf, signal )
         /* val line = */ Line.kr( 0, 0, dur = "$dur".ir, doneAction = freeSelf )
         chanFun( ins.size )
//         out.expand
//         line.expand
      }
   }

   final class GraphFunction[ T ] private[swing]( target: SNode, outBus: Int, fadeTime: Option[ Double ], addAction: AddAction,
                                                  thunk: => T )( implicit result: SGraphFunction.Result.In[ T ]) {
      def waveform( duration: Double = 0.1 ) : Frame = {
         val server     = target.server

         require( server.isLocal, "Currently requires that Server is local" )

         var numChannels= 0
         val sg         = SSynthGraph {
            val r       = thunk
            val signal  = result.view( r )
            GUIRecordOut( signal )( numChannels = _ )
         }
         val ug         = sg.expand
         val defName    = "$swing_waveform" + numChannels
         val sd         = SynthDef( defName, ug )
         val syn        = new SSynth( server )
         val sr         = server.sampleRate
         val numFrames  = math.ceil( duration * sr ).toInt

//         def roundUp( i: Int ) = { val j = i + 32768 - 1; j - j % 32768 }
//
//         val numFramesC = roundUp( numFrames )
//         val durC       = numFramesC / server.sampleRate
         val buf        = Buffer( server )
         val synthMsg   = syn.newMsg( defName, target, List( "$buf" -> buf.id, "$dur" -> duration ), addAction )
         val defFreeMsg = sd.freeMsg
         val compl      = osc.Bundle.now( synthMsg, defFreeMsg )
         val recvMsg    = sd.recvMsg( buf.allocMsg( numFrames, numChannels, compl ))
//         val allocMsg   = buf.allocMsg( numFrames, numChannels,
//            completion = buf.writeMsg( path, numFrames = 0, leaveOpen = true, completion = compl ))

         import WavePainter.MultiResolution

         val path          = File.createTempFile( "scalacollider", ".aif" )

         var paintFun : Graphics2D => Unit = (_) => ()

         lazy val component = new JComponent {
            setPreferredSize( new Dimension( 400, 400 ))
            override def paintComponent( g: Graphics ) {
               val g2 = g.asInstanceOf[ Graphics2D ]
               g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON )
               g2.setRenderingHint( RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE  )
               g2.setColor( Color.black )
               g2.fillRect( 0, 0, getWidth, getHeight )
               paintFun( g2 ) // painter.paint( g2 )
            }
         }

         val box = scala.swing.Component.wrap( component )
         val f = makeFrame( "Plot", "PlotFrame", box, smallBar = false ) {
            path.delete()
         }

         def openBuffer() {
            // println( "JO CHUCK " + path )
            val af = io.AudioFile.openRead( path )
            try {
               val num  = math.min( numFrames, af.numFrames ).toInt
               val data = Array.ofDim[ Float ]( numChannels, num )
               af.read( data, 0, num )
               af.close()
//println( "... read " + num + " frames from " + path.getAbsolutePath )
               val pntSrc  = MultiResolution.Source.wrap( data )
               val place   = new MultiResolution.ChannelPlacement {
                  def rectangleForChannel( ch: Int, result: Rectangle ) {
                     val w             = component.getWidth
                     val h             = component.getHeight
                     val viewHeight    = (h - ((numChannels - 1) * 4)) / numChannels
                     val trackHeight   = viewHeight + 4
                     result.setBounds( 0, trackHeight * ch, w, viewHeight )
                  }
               }
               val painter  = MultiResolution( pntSrc, place )
               painter.startFrame= 0L
               painter.stopFrame = numFrames
               painter.magLow    = -1
               painter.magHigh   = 1
               painter.peakColor = Color.gray
               painter.rmsColor  = Color.white
               paintFun = painter.paint
               component.repaint()

            } finally {
               if( af.isOpen ) af.close()
            }
         }

         syn.onEnd {
            val syncMsg    = server.syncMsg()
            val syncID     = syncMsg.id
            val writeMsg   = buf.writeMsg( path.getAbsolutePath, completion = osc.Bundle.now( buf.freeMsg, syncMsg ))
            server.!?( 10000L, writeMsg, {
               case sosc.SyncedMessage( `syncID` ) => openBuffer()
               case actors.TIMEOUT => println( "Timeout!" )
            })
         }
         server ! recvMsg // osc.Bundle.now( recvMsg, allocMsg )

         f
      }
   }

   private final case class AudioBusMeterConfig( bus: SAudioBus, target: SGroup, addAction: AddAction )

   private def makeAudioBusMeter( server: SServer, name: String, configs: Seq[ AudioBusMeterConfig ]) : Frame = {
      val chans: Set[ Int ] = configs.map( _.bus.numChannels )( breakOut )
      val synthDefs: Map[ Int, SSynthDef ] = chans.map({ numChannels =>
         import synth._
         import ugen._
         val d = SSynthDef( "$swing_meter" + numChannels ) {
            val sig     = In.ar( "bus".ir, numChannels )
            val tr      = Impulse.kr( 20 )
            val peak    = Peak.kr( sig, tr )
            val rms     = A2K.kr( Lag.ar( sig.squared, 0.1 ))
            SendReply.kr( tr, Flatten( Zip( peak, rms )), "/$meter" )
         }
         numChannels -> d
      })( breakOut )

      var newMsgs    = Map.empty[ Int, List[ osc.Message ]]
      var resps      = List.empty[ sosc.Responder ]
      var synths     = List.empty[ SSynth ]
      var meters     = Vector.empty[ PeakMeter ]
      var wasClosed  = false

      configs.foreach { cfg =>
         import cfg._
         val numChannels      = bus.numChannels
         val synth            = new SSynth( target.server )
         val d                = synthDefs( numChannels )
		   val newMsg           = synth.newMsg( d.name, target, Seq( "bus" -> bus.index ), addAction )
         val meter            = new PeakMeter
         meter.numChannels    = numChannels
         meter.hasCaption     = true
         meter.borderVisible  = true

         val resp             = sosc.Responder.add( server ) {
            case Message( "/$meter", synth.id, _, vals @ _* ) =>
               val pairs   = vals.asInstanceOf[ Seq[ Float ]].toIndexedSeq
               val time    = System.currentTimeMillis()
               Swing.onEDT( meter.update( pairs, 0, time ))
         }

         synth.onGo { Swing.onEDT {
            if( wasClosed ) {
               import Ops._
               synth.free()
            } else {
               synths ::= synth
            }
         }}

         newMsgs += numChannels -> (newMsg :: newMsgs.getOrElse( numChannels, d.freeMsg :: Nil ))
         resps  ::= resp
//         synths ::= synth
         meters :+= meter
      }

      val recvMsgs: List[ osc.Message ] = synthDefs.map({ case (numChannels, d) =>
         d.recvMsg( completion = Some( osc.Bundle.now( newMsgs( numChannels ): _* )))
      })( breakOut )

      server ! (recvMsgs match {
         case single :: Nil => single
         case _ => osc.Bundle.now( recvMsgs: _* )
      })

      val box = new BoxPanel( Orientation.Horizontal ) {
         contents ++= meters
      }
      makeFrame( "Meter (" + name + ")", "MeterFrame", box ) {
         resps.foreach( _.remove() )
         meters.foreach( _.dispose() )
         wasClosed      = true
         val freeMsgs   = synths.map( _.freeMsg )
         freeMsgs match {
            case single :: Nil => server ! single
            case Nil =>
            case _ => server ! osc.Bundle.now( freeMsgs: _* )
         }
      }
   }

   private def makeFrame( name: String, string: String, component: scala.swing.Component, smallBar: Boolean = true )
                        ( onClose: => Unit ) : Frame = {
      new Frame {
         if( smallBar ) peer.getRootPane.putClientProperty( "Window.style", "small" )
         title = name
         contents = component
//         new BoxPanel( Orientation.Horizontal ) {
//            contents ++= meters
//         }
         pack().centerOnScreen()
         visible = true

         override def toString() = string + "@" + hashCode().toHexString

         override def closeOperation() {
            onClose
            this.dispose()
         }
      }
   }

   final class Server private[swing] ( val server: SServer ) {
      def tree() { new Group( server.rootNode ).tree() }

      def meter() : Frame = {
         val opt        = server.config
         val numInputs  = opt.inputBusChannels
         val numOutputs = opt.outputBusChannels
         val target     = server.rootNode
         val inBus      = SAudioBus( server, index = numOutputs, numChannels = numInputs )
         val outBus     = SAudioBus( server, index = 0,          numChannels = numOutputs )
         val inCfg      = AudioBusMeterConfig( inBus,  target, addToHead )
         val outCfg     = AudioBusMeterConfig( outBus, target, addToTail )
         makeAudioBusMeter( server, server.toString(), inCfg :: outCfg :: Nil )
      }
   }
}