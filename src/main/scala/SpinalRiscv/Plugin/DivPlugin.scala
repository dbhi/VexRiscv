package SpinalRiscv.Plugin

import SpinalRiscv.{VexRiscv, _}
import spinal.core._
import spinal.lib.math.MixedDivider

class DivPlugin extends Plugin[VexRiscv]{
   object IS_DIV extends Stageable(Bool)

   override def setup(pipeline: VexRiscv): Unit = {
     import Riscv._
     import pipeline.config._

     val actions = List[(Stageable[_ <: BaseType],Any)](
       SRC1_CTRL                -> Src1CtrlEnum.RS,
       SRC2_CTRL                -> Src2CtrlEnum.RS,
       REGFILE_WRITE_VALID      -> True,
       BYPASSABLE_EXECUTE_STAGE -> False,
       BYPASSABLE_MEMORY_STAGE  -> True,
       REG1_USE                 -> True,
       REG2_USE                 -> True,
       IS_DIV                   -> True
     )

     val decoderService = pipeline.service(classOf[DecoderService])
     decoderService.addDefault(IS_DIV, False)
     decoderService.add(List(
       DIVX  -> actions
     ))

   }

   override def build(pipeline: VexRiscv): Unit = {
     import pipeline._
     import pipeline.config._

     val divider = new MixedDivider(32, 32, true) //cmd >-> rsp

     //Send request to the divider component
     execute plug new Area {
       import execute._

       divider.io.cmd.valid := False
       divider.io.cmd.numerator := input(SRC1)
       divider.io.cmd.denominator := input(SRC2)
       divider.io.cmd.signed := !input(INSTRUCTION)(12)

       when(arbitration.isValid && input(IS_DIV)) {
         divider.io.cmd.valid := !arbitration.isStuckByOthers && !arbitration.removeIt
         arbitration.haltIt := memory.arbitration.isValid && memory.input(IS_DIV)
       }
     }

     //Collect response from the divider component, REGFILE_WRITE_DATA overriding
     memory plug new Area{
       import memory._

       divider.io.flush := memory.arbitration.removeIt
       divider.io.rsp.ready := !arbitration.isStuckByOthers

       when(arbitration.isValid && input(IS_DIV)) {
         arbitration.haltIt := !divider.io.rsp.valid

         input(REGFILE_WRITE_DATA) := Mux(input(INSTRUCTION)(13), divider.io.rsp.remainder, divider.io.rsp.quotient).asBits
       }


       divider.io.rsp.payload.error.allowPruning
     }
   }
 }
