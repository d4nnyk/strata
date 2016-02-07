  .text
  .globl target
  .type target, @function

#! file-offset 0
#! rip-offset  0
#! capacity    6 bytes

# Text                              #  Line  RIP   Bytes  Opcode                
.target:                            #        0     0      OPC=<label>           
  pmovzxbq %xmm2, %xmm1             #  1     0     5      OPC=pmovzxbq_xmm_xmm  
  callq .move_128_064_xmm1_r8_r9    #  2     0x5   5      OPC=callq_label       
  movsbq %r8b, %r12                 #  3     0xa   4      OPC=movsbq_r64_r8     
  movsbq %r9b, %r13                 #  4     0xe   4      OPC=movsbq_r64_r8     
  callq .move_064_128_r12_r13_xmm1  #  5     0x12  5      OPC=callq_label       
  retq                              #  6     0x17  1      OPC=retq              
                                                                                
.size target, .-target