public static long CreateProcess(char filename, long filename)
{

	// Allocate space for Process Control Block
      if (PCBptr == 0 || PCBptr == MAXOFADDRESS) 
      {
    	  System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
    	  return AddressInvalidError;
      }

      // Initialize PCB: Set nextPCBlink to end of list, default priority, Ready state, and PID
      long nextPCBlink = EOL;
      EOL = 
      
 
      // Load the program
      Set value =load the program calling by Absolute Loader passing filename as argument;
      Check for error and return error code, if loading program failed

      // store PC value in the PCB of the process
     Set PC value in the PCB = value;  

      // Allocate stack space from user free list
      Set ptr = Allocate User Memory of size StackSize;
       if (ptr < 0)	// check for error
      {  // User memory allocation failed
	Free allocated PCB space by calling Free OS Memory passing PCBptr and  PCBsize;
	return(ptr);  // return error code
      }

      // Store stack information in the PCB – SP, ptr, and size
      Set SP in the PCB = ptr + Stack Size;  // empty stack is high address, full is low address
      Set stack start address in the PCB to ptr;
      Set stack size in the PCB = Stack Size;

      Set priority in the PCB = priority;	// Set priority

     Dump program area;

     Print PCB passing PCBptr; 

     // Insert PCB into Ready Queue according to the scheduling algorithm
     Insert PCB into Ready Queue passing PCBptr;

     return(OK);
}  // end of CreateProcess() function


