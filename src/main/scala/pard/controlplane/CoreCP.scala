// See LICENSE for license details.

package pard

import Chisel._
import cde.{Parameters, Field}
import rocketchip.{ExtMemSize, ExtMemBase}
import junctions.{PAddrBits}

class DsidConfigIO(implicit p: Parameters) extends ControlPlaneBundle {
  val dsids = Vec(nTiles, UInt(OUTPUT, width = p(LDomDsidBits)))
  override def cloneType = (new DsidConfigIO).asInstanceOf[this.type]
}

class HartidConfigIO(implicit p: Parameters) extends ControlPlaneBundle {
  val hartids = Vec(nTiles, UInt(OUTPUT, width = cpDataSize))
  override def cloneType = (new HartidConfigIO).asInstanceOf[this.type]
}

class AddressMapperConfigIO(implicit p: Parameters) extends ControlPlaneBundle {
  val bases = Vec(nTiles, UInt(OUTPUT, width = cpDataSize))
  val sizes = Vec(nTiles, UInt(OUTPUT, width = cpDataSize))
  override def cloneType = (new AddressMapperConfigIO).asInstanceOf[this.type]
}

class CoreControlPlaneIO(implicit p: Parameters) extends ControlPlaneBundle {
  val rw = (new ControlPlaneRWIO).flip
  val dsidConfig = new DsidConfigIO
  val hartidConfig = new HartidConfigIO
  val addressMapperConfig = new AddressMapperConfigIO
}

class CoreControlPlaneModule(implicit p: Parameters) extends ControlPlaneModule {
  val io = IO(new CoreControlPlaneIO)
  val cpIdx = UInt(coreCpIdx)

  // ptab
  val dsidCol = 0
  val baseCol = 1
  val sizeCol = 2
  val hartidCol = 3

  val ptabDsidRegs   = Reg(Vec(nTiles, UInt(width = p(LDomDsidBits))))
  val ptabBaseRegs   = Reg(Vec(nTiles, UInt(width = p(PAddrBits))))
  val ptabSizeRegs   = Reg(Vec(nTiles, UInt(width = p(PAddrBits))))
  val ptabHartidRegs = Reg(Vec(nTiles, UInt(width = p(LDomDsidBits))))

  when (reset) {
    for (i <- 0 until nTiles) {
      ptabHartidRegs(i) := UInt(i)
      if (p(UseNoHype)) {
        if (p(UseSim)) {
          val size = p(ExtMemSize) / nTiles
          ptabDsidRegs(i) := UInt(i)
          ptabBaseRegs(i) := UInt(size * i)
          ptabSizeRegs(i) := UInt(size)
        }
      }
      else {
        ptabDsidRegs(i) := UInt(0)
        ptabBaseRegs(i) := UInt(0)
        ptabSizeRegs(i) := UInt(p(ExtMemSize))
      }
    }
  }

  io.rw.rready := true.B

  // read
  val rrow = getRowFromAddr(io.rw.raddr)
  val rcol = getColFromAddr(io.rw.raddr)
  val rdata = Mux(io.rw.ren, MuxLookup(rcol, UInt(0), Array(
    UInt(dsidCol)   -> ptabDsidRegs(rrow),
    UInt(baseCol)   -> ptabBaseRegs(rrow),
    UInt(sizeCol)   -> ptabSizeRegs(rrow),
    UInt(hartidCol) -> ptabHartidRegs(rrow)
  )), UInt(0))
  io.rw.rdata := RegNext(rdata)

  // write
  val wrow = getRowFromAddr(io.rw.waddr)
  val wcol = getColFromAddr(io.rw.waddr)
  when (io.rw.wen) {
    switch (wcol) {
      is (UInt(dsidCol))   { ptabDsidRegs(wrow)   := io.rw.wdata }
      is (UInt(baseCol))   { ptabBaseRegs(wrow)   := io.rw.wdata }
      is (UInt(sizeCol))   { ptabSizeRegs(wrow)   := io.rw.wdata }
      is (UInt(hartidCol)) { ptabHartidRegs(wrow) := io.rw.wdata }
    }
  }

  // wire out cpRegs
  (io.dsidConfig.dsids zip ptabDsidRegs) ++
    (io.hartidConfig.hartids zip ptabHartidRegs) ++
    (io.addressMapperConfig.bases zip ptabBaseRegs) ++
    (io.addressMapperConfig.sizes zip ptabSizeRegs) map { case (o, i) => o := i }
}
