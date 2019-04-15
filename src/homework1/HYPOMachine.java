/*
 * Author Name:	Jonathon Ku and Gabe and India
 * Student ID:	300994041
 * Class:		CSCI465
 * Assignment:	Homework
 * Date:		2/8/2019
 * Description:	This program simulates the operation of a CPU within the main method. 
 * 				It first utilizes the InitializeSystem method to set all simulated 
 * 				hardware to values of 0. It then reads in a "program"
 * 				coded in machine language, specified by the user, and uses the
 * 				AbsoluteLoader method to "load" the program labels and instructions
 * 				into the simulated Main Memory. It then utilizes the ExecuteProgram
 * 				method to fetch, decode and execute instructions. To determine the
 * 				the operand's address and value, we utilize a FetchOperand method
 * 				and local Operand class to return the operand's value and location.
 * 				During loading and execution, HYPOMachine checks for potential runtime
 * 				errors. If one is incurred, it displays a message, returns an error code
 * 				and then stops the HYPOMachine. In order to display all the contents
 * 				of memory neatly, there is a DumpMemory method, which prints a well
 * 				formatted output of global variables, and a range of memory which can
 * 				be specified by the programmer. 
 */

/*
 * Change Log:
 * 
 * 3/2/2019- JKU: I updated InitializeSystem() with PID, UserFreeList and OSFreeList. 
 * Also added EOL and TIMESLICE to global variables. Also wrote AllocateOSMemory method
 *
 * 3/3/2019- JKU: Wrote FreeOSMemory method. Added constant variables for the minimum
 * and maximum addresses of each section of memory: User Program, User Dynamic, OS
 * Dynamic. Added error code for Success! Additional constant variables for the future
 * will include PCB indices for Priority, Size, NextPCB, etc.
 * 
 * 3/4/2019- JKU: Added final variables for Indices for consistent access of PCB items: 
 * PID, State, Priority. Also added final variables for the three states of a PCB. Wrote
 * InsertIntoRQ() method.
 * 
 * 3/6/2019- JKU: Added WQ variable. Wrote InsertIntoWQ() method. Wrote FreeUserMemory()
 * method. Adjusted FreeUserMemory() and FreeOSMemory() logical statments.
 * 
 * 3/7/2019- JKU: Wrote SearchAndRemovePCBFromWQ() method.
 * 
 */

//import java.util.LinkedList;

package homework1;
import java.util.Scanner;
import java.io.*;

public class HYPOMachine 
{
	//Global Variables
	final private static long[] MAINMEMORY = new long[10000];	//Array of memory addresses
	final private static long[] GPRS = new long [8];			//Array of temporary registers for quick memory access
	final private static long EOL = -1;							//EndOfList indicator
	final private static long TIMESLICE = 200;					//For Priority Round Robin Algorithm. Amount of clock ticks before CPU is given to another process.
	final private static long MINPROGRAMADDRESS = 0;			//Minimum address for User Program block of memory
	final private static long MAXPROGRAMADDRESS = 2999;			//Maximum address for User Program block of memory
	final private static long MINUSERMEMADDRESS = 3000;			//Minimum address for User Memory block of memory			
	final private static long MAXUSERMEMADDRESS = 6999;			//Maximum address for User Memory block of memory
	final private static long MINOSMEMADDRESS = 7000;			//Minimum address for OS Memory block of memory
	final private static long MAXOSMEMADDRESS = 9999;			//Maximum address for OS Memory block of memory
	
	final private static long PCBNEXTPCBINDEX = 0;				//Index of Next PCB Address in a PCB
	final private static long PCBPIDINDEX = 1;					//Index of PID in a PCB
	final private static long PCBSTATEINDEX = 2;				//Index of State in PCB
	final private static long PCBREASONFORWAITINGINDEX = 3;		//Index of Reason for Waiting in PCB
	final private static long PCBPRIORITYINDEX = 4;				//Index of Priority in PCB
	final private static long PCBSTACKSTARTINDEX = 5;			//Index of PCB stack start address
	final private static long PCBSTACKSIZEINDEX = 6;			//Index of PCB stack size
	final private static long PCBGPR0 = 10;						//Index of PCB GPR 0
	final private static long PCBGPR1 = 11;						//Index of PCB GPR 1
	final private static long PCBGPR2 = 12;						//Index of PCB GPR 2
	final private static long PCBGPR3 = 13;						//Index of PCB GPR 3
	final private static long PCBGPR4 = 14;						//Index of PCB GPR 4
	final private static long PCBGPR5 = 15;						//Index of PCB GPR 5
	final private static long PCBGPR6 = 16;						//Index of PCB GPR 6
	final private static long PCBGPR7 = 17;						//Index of PCB GPR 7
	final private static long PCBSPINDEX = 18;					//Index of PCB SP
	final private static long PCBPCINDEX = 19;					//Index of PCB PC
	final private static long PCBPSRINDEX = 20;					//Index of PCB PSR
	final private static long READYSTATE = 1; 					//Value to indicate Ready State of a PCB
	final private static long RUNNINGSTATE = 2;					//Value to indicate Running State of a PCB
	final private static long WAITINGSTATE = 3;					//Value to indicate Waiting State of a PCB
	final private static long DEFAULTPRIORITY = 128;			//Default Priority of Program
	final private static String NULLPROCESS = "nullProcess.txt";//Filename for the null process to continually run.
	final private static long OSMode = 1;						//Represents System in OS control
	final private static long UserMode = 2;						//Represents System in User control
	final private static long PCBSIZE = 20;						//Size of a PCB
	final private static long PCBSTACKSIZE = 200;				//Size of stack allocated for PCB
	final private static long STARTOFINPUTEVENT = 3;			
	final private static long STARTOFOUTPUTEVENT = 4;

	private static long CLOCK;									//Keeps track of how long it has taken for execution
	private static long MAR;									//Contains the current address of instruction in main memory
	private static long MBR;									//Contains the content of current address
	private static long IR;										//Contains instruction to decode and execute
	private static long PSR;									//Contains a value based on the state of a process. 0 if no errors incurred. List of error codes below.
	private static long PC;										//Contains the memory address of the instruction being executed
	private static long SP;										//Points to the current address of the stack in memory
	private static long RQ = EOL;								//Pointer to the head of the Ready Queue
	private static long WQ = EOL;								//Pointer to the head of the Wait Queue
	private static long UserFreeList = EOL;						//Pointer to the head of User Free Memory List
	private static long OSFreeList = EOL;						//Pointer to the head of OS Free Memory List
	private static long PID;									//Keeps track of next Process ID is available.
	
	/*****************************************************************************
	 * Error Codes
	 * 		 0:		Success							No error. Operation was successful.
	 * 		-1:		FileOpenError					Unable to open the file
	 * 		-2:		AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999 
	 * 		-3:		InvalidPCValueError				Invalid PC value. Value must be between 0-2999
	 * 		-4: 	NoEndOfProgramError				Missing end of program indicator
	 * 		-5:		InvalidInstructionError			Invalid instruction error. Value must be between 0 and 126767
	 * 		-6: 	InvalidOpcodeError				Invalid OpCode. OpCode must be between 0-12
	 * 		-7: 	InvalidOpModeError				Invalid OpMode. OpMode must be between 0-6
	 * 		-8:		InvalidOpGPRError				Invalid OpGPR. OpGPR must be between 0-7
	 * 		-9: 	DestinatinoOperandError			Invalid destination operand.
	 * 		-10:	DivisionByZeroError				Cannot divide by zero.
	 * 		-11:	StackOverFlowError				Stack is full. Max address 9999.
	 * 		-12:	StackUnderFlowError				Stack is empty. Bottom of stack is 9900.
	 * 		-13:	NoFreeMemoryError				No free memory to allocate from list.
	 * 		-14:	InvalidMemorySizeError			Invalid Memory Size. Size must be greater than 0.
	 * 		-15: 	InvalidPIDError					Invalid PID. Not found in queue.
	 * 		-16:	InvalidSystemCallID				Invalid System Call ID. Only implemented 1-9.
	 * 		-17:	ShutdownError					System Shutdown Interrupt invoked. Shutdown system
	 *		-18:	TimeSliceExpiredError			TimeSlice allocated for process has expired. Release CPU to another process.
	 *****************************************************************************/
	final static private long Success = 0;
	final static private long FileOpenError = -1;
	final static private long AddressInvalidError = -2;
	final static private long InvalidPCValueError = -3;
	final static private long NoEndOfProgramError = -4;
	final static private long InvalidInstructionError = -5;
	final static private long InvalidOpcodeError = -6;
	final static private long InvalidOpModeError = -7;
	final static private long InvalidOpGPRError = -8;
	final static private long DestinatinoOperandError = -9;
	final static private long DivisionByZeroError = -10;			
	final static private long StackOverFlowError = -11;	
	final static private long StackUnderFlowError = -12;
	final static private long NoFreeMemoryError = -13;
	final static private long InvalidMemorySizeError = -14;
	final static private long InvalidPIDError = -15;
	final static private long InvalidSystemCallID = -16;
	final static private long ShutdownError = -17;
	final static private long TimeSliceExpiredError = -18;
	
	/*****************************************************************************
	 * Function: InitializeSystem
	 * 
	 * Task Description:
	 * 		Sets all global simulated hardware components to 0
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		None
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/8/2019: Created loop to initialize MAINMEMORY and GPRS global arrays.
	 * 		Additionally, initialize all other global variables to 0.
	 * 		3/2/2019: Added instantiation for PID, OSFreeList, and UserFreeList
	 *****************************************************************************/	
	private static void InitializeSystem() 
	{
		for(int i = 0; i < MAINMEMORY.length; i++)
		{
			MAINMEMORY[i] = 0;
		}
		for(int j = 0; j < GPRS.length; j++)
		{
			GPRS[j] = 0;
		}
		CLOCK = 0;
		MAR = 0;
		MBR = 0;
		IR = 0;
		PSR = 0;
		PC = 0;
		SP = 0;
		PID = 0;

		/*
		 * Create UserFreeList: Point UserFreeList to the address of the beginning of
		 * the User Memory section of main memory. Store in a constant variable, 
		 * MINUSERMEMADDRESS. Store size of block in next index, calculated by max
		 * address + min address + 1.
		 */
		
		UserFreeList = MINUSERMEMADDRESS;
		MAINMEMORY[(int) UserFreeList] = EOL;
		MAINMEMORY[(int) UserFreeList + 1] = MAXUSERMEMADDRESS - MINUSERMEMADDRESS + 1;
		/*
		 * Create OSFreeList: Point OSFreeList to the address of the beginning of
		 * the OS Memory section of main memory. Store in a constant variable, 
		 * MINOSMEMADDRESS. Store size of block in next index, calculated by max
		 * address + min address + 1.
		 */
		OSFreeList = MINOSMEMADDRESS;
		MAINMEMORY[(int) OSFreeList] = EOL;
		MAINMEMORY[(int) OSFreeList + 1] = MAXOSMEMADDRESS - MINOSMEMADDRESS + 1; 
		//Create process using Null Process file to continually run system until interrupt or new process is created.
		CreateProcess(NULLPROCESS, 0);
	}

	/*****************************************************************************
	 * Function: DumpMemory
	 * 
	 * Task Description:
	 * 		Displays a string passed as one of the input parameter. Displays the contents
	 * 		of GPRs, SP, PC, PSR, system Clock and the content of specified memory 
	 * 		locations in a specific format.
	 * 
	 * Input Parameters:
	 * 		String					String to be displayed
	 * 		StartAddress			Start address of memory location
	 * 		Size					Number of locations to dump
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		None
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/10/2019: Created the DumpMemory method. Runs as expected. Tested with
	 * 		negative starting address and >10000 ending address. 
	 * 
	 *****************************************************************************/
	private static void DumpMemory(String display, long startAddress, long size)
	{
		//Local Variables
		long endAddress = startAddress + size;	//Generate an ending address by adding size to start address
		
		System.out.println(display); 			//Display the preceding display text
		/*Check that address is a valid address. Must be greater than 0.
		 *Display informative error message.
		 */
		if(startAddress < 0) 					
		{
			System.out.println("Invalid start address. Address must be >= 0");
			return;
		}
		/*Check that starting address is a valid address. Must be less than 10000.
		 *Display informative error message.
		 */
		else if(startAddress >= 10000)			
		{
			System.out.println("Invalid start address. Address must be < 10000");
			return;
		}
		/*Check that ending address remains within the valid address range. Display
		 *informative error message.
		 */
		else if(endAddress >= 10000)
		{
			System.out.println("Invalid size. Size must ensure end address stays within the range 0-9999");
			return;
		}
		/* Display GPR header. On next line offset by two tabs to align with G0.
		 * Iterate through GPRs and display their values. Append SP and PC to the string
		 * Print address header. Then display memory location contents.
		 */
		else
		{
			
			System.out.println("GPRs:\t\tG0\tG1\tG2\tG3\tG4\tG5\tG6\tG7\tSP\tPC");
			System.out.print("\t\t");
			for(int header = 0; header < GPRS.length; header++)
			{
				System.out.print(GPRS[header] + "\t");
			}
			System.out.println(SP + "\t" + PC);
			
			/* set addr to start address as it will keep track of what address we are
			 * currently displaying. In order to get the left aligned tens place tracker
			 * divide startAddress by 10 and multiply by 10, store in blank.
			 */
			System.out.println("Address:\t+0\t+1\t+2\t+3\t+4\t+5\t+6\t+7\t+8\t+9");	
			int addr = (int)startAddress;
			long blank = (startAddress/10)*10;
			
			/* Continually loop until addr reaches ending address. Print the blank
			 * and tab twice to align with +0. If blank + iterator variable value is
			 * less than starting address, tab, so we do not display unwanted locations.
			 * Each time addr is greater than blank + value and addr less than or equal
			 * to ending address, print contents of memory location, tab, and increment 
			 * addr. Each time the for loop ends, increment blank by 10 to get next tens
			 * place tracker.
			 * When addr is no longer less than or equal to ending address, break the
			 * loop. And print Clock and PSR values.   
			 */
			while(addr < endAddress) 
			{
				System.out.print(blank + "\t\t");
				for(int value = 0; value < 10; value++)
				{
					if(blank+value < addr) 
					{
						System.out.print("\t");
					}
					else if(addr <= endAddress)
					{
						System.out.print(MAINMEMORY[addr] + "\t");
						addr++;
					}
					else
					{
						break;
					}
				}
				System.out.println();
				blank += 10;
			}
			System.out.println("Clock:\t" + CLOCK + "\tPSR:\t" + PSR);
		}
			
	}
	
	/*****************************************************************************
	 * Function: AbsoluteLoader
	 * 
	 * Task Description:
	 * 		Opens file containing HYPO machine language which is specified by user
	 * 		in main function. It then loads the content into HYPO memory.
	 * 		On successful load, it returns the PC value in the End of Program line.
	 * 		On failure, it displays an appropriate error message and returns its
	 * 		corresponding error code
	 * 
	 * Input Parameters:
	 * 		filename					Name of the HYPO machine language file
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value will be one of the following:
	 *		-1:	FileOpenError			Unable to open the file
	 *		-2:	AddressInvalidError		Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 *		-3:	InvalidPCValueError		Invalid PC value. Value must be between 0 and 9999
	 *		-4: NoEndOfProgramError		Missing end of program indicator
	 * 		0 - Valid Address Range		Successful Load, valid PC value
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/8/2019: I began developing a code which worked like the assembler, because
	 * 		I totally misunderstood the function of the AbsoluteLoader function in
	 * 		documentation. This involved writing to a few files. I've noted which
	 * 		commented code was for this "Assembler Function" because I'm too proud
	 * 		of it to delete it. Maybe I'll finish it later
	 * 		2/10/2019: Put a Scanner in to read the fileName Input parameter and
	 * 		implemented a try/catch structure to try and read the file. Then read
	 * 		line by line and use the address within the file an index for the MAINMEMORY
	 * 		array. Then take the instruction corresponding to each address and use it
	 * 		assign it as the element for the index. I put checks for each possible 
	 * 		errors which might be caused.
	 *****************************************************************************/
	private static long AbsoluteLoader(String fileName)
	{
		//Local Variables
		String temp = "";	//Used to contain each line of the file without redeclaring temp
		try 
		{
			//Declare and initialize a scanner to try and read file specified by fileName
			Scanner fileReader = new Scanner(new File(fileName));	
			//Loop through file line by line until end of file or end of program indicator
			while(fileReader.hasNextLine())
			{
				/* Set temp to the next line in file, then split by the \t character and
				 * store in array called line element 0 contains the address and
				 * element 1 contains the content.
				 */
				temp = fileReader.nextLine();
				String[] line = temp.split("\t");
				/* If we have reached the end of the file and the address is equal to -1
				 * check to ensure that the content, which is the PC value, is a valid
				 * address in memory (it cannot be less than 0 or greater than or equal to 10000).
				 * Otherwise, the PC is not a valid address, display an error message and
				 * return appropriate error code.
				 */
				if(!fileReader.hasNextLine() && line[0].equals("-1"))
				{
					if(Integer.parseInt(line[1]) < 3000 && Integer.parseInt(line[1]) >= 0) 
					{
						fileReader.close();
						return Integer.parseInt(line[1]);
					}
					else 
					{
						System.out.println("Invalid PC value. Value must be between 0 and 9999");
						fileReader.close();
						return InvalidPCValueError;
					}
				}
				/* Check to ensure that the address is a valid address. It must be greater than
				 * or equal to 0 or less than 3000 (2999 is the Maximum Program Address). If it is, set the appropriate address in
				 * MAINMEMORY to equal the instruction
				 */
				else if(Integer.parseInt(line[0]) >= 0 && Integer.parseInt(line[0]) < 3000)
				{
					int address = Integer.parseInt(line[0]);
					long instruction = Long.parseLong(line[1]);
					MAINMEMORY[address] = instruction;
				}
				/* If the address is not valid. Display error message and return
				 * error code.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					fileReader.close();
					return AddressInvalidError;
				}		
			}
			/* If file is read through without finding an end of program indicator
			 * display error message return error code.
			 */
			System.out.println("End of program reached without indicator");
			fileReader.close();
			return NoEndOfProgramError;
			
		}
		/* If FileNotFoundException is caught, display error message and return error.
		 * If program reached outside of the try/catch structure, file is also not read
		 * and displays the same message.
		 */
		catch(Exception e)
		{
			if(e instanceof FileNotFoundException)
			{
				System.out.println("File open error.");
				return FileOpenError;
			}
		}
		System.out.println("File open error.");
		return FileOpenError;
	}
	
	/*****************************************************************************
	 * Function: ExecuteProgram
	 * 
	 * Task Description:
	 * 		This function uses the PC value to determine where in memory the initial
	 * 		instruction is. Then it fetches the appropriate instruction, decodes the
	 * 		instruction, then executes the instruction by manipulating the appropriate
	 * 		memory location contents and GPR contents
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		-2:		AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 		-3:		InvalidPCValueError				Invalid PC value. Value must be between 0 and 9999
	 * 		-5:		InvalidInstructionError			Invalid instruction error. Value must be between 0 and 126767
	 * 		-6: 	InvalidOpcodeError				Invalid OpCode. OpCode must be between 0-12
	 * 		-7: 	InvalidOpModeError				Invalid OpMode. OpMode must be between 0-6
	 * 		-8:		InvalidOpGPRError				Invalid OpGPR. OpGPR must be between 0-7
	 * 		-9: 	DestinatinoOperandError			Invalid destination operand.
	 * 	 	-10:	DivisionByZeroError				Cannot divide by zero.
	 * 		-11:	StackOverFlowError				Stack is full. Max address 9999.
	 * 		-12:	StackUnderFlowError				Stack is empty. Bottom of stack is 9900
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/10/2019: Wrote do while loop to continually iterate through the memory
	 * 		using PC. Each iteration, the function would fetch the instruction from
	 * 		memory, decode it into opcode, operand 1 mode & gpr, operand 2 mode & gpr.
	 * 		Then it would go into a switch where it would use the opcode to determine
	 * 		the executable instructions. Any errors incurred set status to a negative
	 * 		value, causing the loop to end. The loop continues until an error occurs,
	 * 		or a halt is reached by storing 0 in MBR. Then it returns the status.
	 *****************************************************************************/	
	/* Function: PrintPCB
	 * 
	 * Task Description:
	 * 		printing out all the values in a PCB process
	 * 
	 * Input:
	 * 		Process, PID, PCB indexes, state indexes
	 * 
	 * Output:
	 * 		None
	 * 
	 * Author: India Ervin
	 * 
	 * 
	 */
	public void PrintPCB(long PCBptr)
	{
		/*Print the values of the following fields from PCB with a text before the value like below:
		*	PCB address = 6000, Next PCB Ptr = 5000, PID = 2, State = 2, PC = 200, SP = 4000, 
		*	Priority = 127, Stack info: start address = 3990, size = 10
		*	GPRs = print 8 values of GPR 0 to GPR 7
		*/
		
		/*
		 * Use of a StringBuilder to account for the many values needed
		 * to print the PCB
		 */
		StringBuilder pcbFormat = new StringBuilder("PCB Address = ");
				pcbFormat.append(PCBPtr + ",");
				pcbFormat.append("Next PCB Pointer = " + PCBNEXTPCBINDEX + ",");
				pcbFormat.append("PID = " + PID + ",");
				
				/* Use of a switch-case block to account
				 * for the 3 possible state of the PCB process
				 */
				switch(PCBSTATEINDEX)
				{
					case 0:
						pcbFormat.append("State= " + READYSTATE + ",");
						break;
					
					case 1:
						pcbFormat.append("State= " + RUNNINGSTATE + ",");
						break;
						
					case 2:
						pcbFormat.append("State= " + WAITINGSTATE + ",");
						break;
				}
				
				pcbFormat.append("PC = "  + PC + ",");
				pcbFormat.append("SP = " + SP);
				pcbFormat.append("Priority = " + PCBPRIORITYINDEX + ",");
				pcbFormat.append("Stack info: Start address = " + MINUSERMEMADDRESS + ",");
				pcbFormat.append("Size = " + MAINMEMORY[(MAR)] + ",");
				pcbFormat.append("GPRs = " + GPRS[i] + ",");
				
				System.out.print(pcbFormat.toString());
	}
	private static long ExecuteProgram()
	{
		//Local Variables
		long opCode;		//Store Operation Code of instruction in opCode
		long op1Mode;		//Store Operand 1 Mode of instruction in op1Mode
		long op1GPR;		//Store Operand 1 GPR of instruction in op1GPR
		long op2Mode;		//Store Operand 2 Mode of instruction in op2Mode
		long op2GPR;		//Store Operand 2 GPR of instruction in op2GPR
		long timeLeft = TIMESLICE; //Timeslice is a constant of 200 clock ticks
		
		long systemCallID;	//Used for OpCode 12, System Call.
		long result = 0;	//Stores result of arithmetic operations
		long remainder = 0;	//Used to contain the remainder of integer divisions when separating instruction into 5 parts.
		long status = 0;	//Contains status error codes, if errors occur, otherwise, remains 0. Returned to calling function.
		Operand op1;		//Used to retrieve Operand 1 value, address and fetching completion status.
		Operand op2;		//Used to retrieve Operand 2 value, address and fetching completion status.
		
		do 
		{
			//Fetch Cycle
			if(0 <= PC && PC < 3000)
			{
				MAR = PC;
				PC++;
				MBR = MAINMEMORY[(int)MAR];
			}
			else if(MBR > 126767 || MBR < 0) 
			{
				System.out.println("The instruction is not a valid instruction. Value must be between 0 and 126767");
				status =  InvalidInstructionError;
			}
			else
			{
				System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
				status =  AddressInvalidError;
			}
			
			//Decode Cycle
			IR = MBR;
			opCode = IR/10000;
			remainder = IR%10000;
			op1Mode = remainder/1000;
			remainder = remainder%1000;
			op1GPR = remainder/100;
			remainder = remainder%100;
			op2Mode = remainder/10;
			op2GPR = remainder%10;
			
			if(opCode > 12)
			{
				System.out.println("Invalid OpCode. OpCode must be between 0-12");
				status =  InvalidOpcodeError;
			}
			else if(op1Mode > 6 || op2Mode > 6 || op1Mode < 0 || op2Mode < 0)
			{
				System.out.println("Invalid OpMode. OpMode must be between 0-6");
				status =  InvalidOpModeError;
			}
			else if(op1GPR > 7 || op2GPR > 7 || op1GPR < 0|| op2GPR < 0)
			{
				System.out.println("Invalid Op2GPR. OpGPR must be between 0-7");
				status =  InvalidOpGPRError;
			}
			else 
			{
				//Execute Cycle
				switch((int)opCode)
				{
					case 0: //Halt
						System.out.println("Halt Encountered");
						CLOCK+=12;
						timeLeft -= 12;
						break;
					case 1:	//Add
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							result = op1.getValue()+op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=3;
						timeLeft -=3;
						break;
					case 2: //Subtract
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							result = op1.getValue()-op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=3;
						timeLeft -=3;
						break;
					case 3: //Multiply
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							result = op1.getValue()*op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=6;
						timeLeft -=6;
						break;
					case 4:	//Divide
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else if(op2.getValue() == 0)
						{
							System.out.println("Cannot divide by zero error.");
							status =  DivisionByZeroError;
						}
						else
						{
							result = op1.getValue()/op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=6;
						timeLeft -=6;
						break;
					case 5:	//Move
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for valid destination of op2's value.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = op2.getValue();
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = op2.getValue();
							}
						}
						CLOCK +=2;
						timeLeft -=2;
						break;
					case 6: //Branch or Jump instruction
						/* If PC address currently contained in PC is a valid PC address, set PC
						 * to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						if(PC >= 0 && PC < 10000)
						{
							PC = MAINMEMORY[(int)PC];
						}
						else
						{
							System.out.println("Invalid PC value. Value must be between 0 and 9999");
							status =  InvalidPCValueError;
						}
						CLOCK +=2;
						timeLeft -=2;
						break;
					case 7: //Branch on Minus
						/* Fetch operand1 check if no error occurred while fetching operand. Compare
						 * op1 < 0. If true, and PC address currently contained in PC is a valid 
						 * PC address, set PC to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(op1.getValue() < 0)
							{
								if(PC >= 0 && PC < 10000)
								{
									PC = MAINMEMORY[(int)PC];
								}
								else
								{
									System.out.println("Invalid PC value. Value must be between 0 and 9999");
									status =  InvalidPCValueError;
								}
							}
							else
							{
								PC++;
							}
						}
						CLOCK +=4;
						timeLeft -=4;
						break;
					case 8:	//Branch on Plus
						/* Fetch operand1 check if no error occurred while fetching operand. Compare
						 * op1 > 0. If true, and PC address currently contained in PC is a valid 
						 * PC address, set PC to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(op1.getValue() > 0)
							{
								if(PC >= 0 && PC < 10000)
								{
									PC = MAINMEMORY[(int)PC];
								}
								else
								{
									System.out.println("Invalid PC value. Value must be between 0 and 9999");
									status =  InvalidPCValueError;
								}
							}
							else
							{
								PC++;
							}
						}
						CLOCK +=4;
						timeLeft -=4;
						break;
					case 9:	//Branch on zero
						/* Fetch operand1 check if no error occurred while fetching operand. Compare
						 * op1 = 0. If true, and PC address currently contained in PC is a valid 
						 * PC address, set PC to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(op1.getValue() > 0)
							{
								if(PC >= 0 && PC < 10000)
								{
									PC = MAINMEMORY[(int)PC];
								}
								else
								{
									System.out.println("Invalid PC value. Value must be between 0 and 9999");
									status =  InvalidPCValueError;
								}
							}
							else
							{
								PC++;
							}
						}
						CLOCK +=4;
						timeLeft -=4;
						break;
					case 10: //Push
						/* Fetch operand 1. Check if no error occurred while fetching 
						 * operand. Check if SP is currently pointing at an address larger
						 * than stack limit of 9999, if not, increment SP and set the content
						 * of main memory at SP is operand 1 value.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(SP+1 > 9999) 
							{
								System.out.println("Stack Overflow Error. Stack is full max address 9999");
								status =  StackOverFlowError;
							}
							else
							{
								SP++;
								MAINMEMORY[(int)SP] = op1.getValue();
							}
						}
						CLOCK +=2;
						timeLeft -=2;
						break;
					case 11: //Pop
						/* Fetch operand 1. Check if no error occurred while fetching 
						 * operand. Check if SP is currently pointing at an address smaller
						 * than stack limit of 9900, if not, set the content
						 * of main memory at SP is operand 1 value and decrement SP .
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(SP-1 < 9900) 
							{
								System.out.println("Stack Underflow Error. Stack is empty. Nothing to pop");
								status =  StackUnderFlowError;
							}
							else
							{
								if(op1Mode == 1)
								{
									GPRS[(int)op1GPR] = MAINMEMORY[(int)SP];
								}
								else if(op1Mode == 6)
								{
									System.out.println("Destination Operand cannot be immediate value");
									status =  DestinatinoOperandError;
								}
								else
								{
									MAINMEMORY[(int)op1.getAddress()] = MAINMEMORY[(int)SP];
								}
								SP--;
							}
						}
						CLOCK +=2;
						timeLeft -=2;
						break;
					case 12: //System Call
						/* If PC address currently contained in PC is a valid PC address, set PC
						 * to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */


						op1 = FetchOperand(op1Mode, op1GPR);
						status = op1.getStatus(); 
						if(status < 0)
						{
							return(status);
						}
						status = SystemCall(op1.getValue());
						CLOCK +=12;
						timeLeft -=12;
						break;
					default:	//Invalid Opcode
						System.out.println("Invalid OpCode. OpCode must be between 0-12");
						status =  InvalidOpcodeError;
						break;
				}
			}
				
		}while(MBR != 0 && status == 0 && timeLeft > 0);
		if(timeLeft <= 0)
		{
			status = TimeSliceExpiredError;
		}
		return status;
	}
	
	/*****************************************************************************
	 * Function: FetchOperand
	 * 
	 * Task Description:
	 * 		This function takes the OpMode and OpGPR from the ExecuteProgram function's
	 * 		decode cycle. It then depending on the mode, it generates a local Operand
	 * 		object which contains the address, value of the operand, and the status
	 * 		of the fetch (success or error code). Then it returns the Operand object to 
	 * 		the ExecuteProgram function.
	 * 
	 * Input Parameters:
	 * 		OpMode					Operand mode value
	 * 		OpReg					Operand GPR value
	 * 
	 * Output Parameters:
	 * 		Operand.getAddress()					Operand object containing address of operand
	 * 		Operand.getValue()						Operand object containing address of value
	 * 
	 * Function Return Value 
	 * 		Operand.getStatus()						Operand object containing status of fetch
	 * 		Possible values of Operand.getStatus():
	 * 			 0.	Successful Fetch
	 * 			-2:	AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 			-3:	InvalidPCValueError				Invalid PC value. Value must be between 0 and 9999
	 * 			-7: InvalidOpModeError				Invalid OpMode. OpMode must be between 0-6
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/10/2019: Wrote the switch to handle the different opcodes. If it is
	 * 		an invalid op code, return appropriate error message. Otherwise, gather the
	 * 		operand address and value according to the opcode.
	 *****************************************************************************/
	private static Operand FetchOperand(long opMode, long opGPR)
	{
		//Local Variables
		long address;	//Contains address of operand. It can be fetched from GPR, from Main Memory, or from Instruction
		switch((int)opMode) 
		{
			case 1:	//Register mode: operand value is in GPR. No address is given
				return new Operand(0, -1, GPRS[(int)opGPR]);
			case 2: //Register deferred mode: operand address is in GPR and operand value is in memory
				address = GPRS[(int)opGPR];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 3:	//Autoincrement mode: operand address is in GPR and operand value is in memory
				address = GPRS[(int)opGPR];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					GPRS[(int)opGPR] = GPRS[(int)opGPR] + 1;
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 4:	//Autodecrement mode: operand address is in GPR and operand value is in memory 
				address = GPRS[(int)opGPR];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					GPRS[(int)opGPR] = GPRS[(int)opGPR] - 1;
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 5:	//Direct mode: operand address is pointed at by PC and operand value is in main memory at that address
				address = MAINMEMORY[(int)PC++];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 6:	//Intermediate mode: operand is being pointed at by PC. No operand address.
				//Check if PC is valid
				if(PC >= 0 &&PC < 10000)
				{
					return new Operand(0, -1, MAINMEMORY[(int)PC++]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid PC value. Value must be between 0 and 9999");
					return new Operand(InvalidPCValueError, -1, -1);
				}
			default:
				/* If a case is not reached, opcode is not valid, display error message and 
				 * return operand object containing appropriate error status code.
				 */
				System.out.println("Invalid OpMode. OpMode must be between 0-6");
				return new Operand(InvalidOpModeError, -2, -2);
		}
	}

	/*****************************************************************************
	 * Function: InsertIntoRQ
	 * 
	 * Task Description:
	 * 		Takes a parameter long PCBptr which points to an address in memory
	 * 		containing a PCB and inserts it into RQ. RQ will be ordered used for 
	 * 		Priority Round Robin CPU scheduling, therefore we will insert the PCB
	 * 		into RQ so the highest priority PCB is at the head.
	 * 
	 * Input Parameters:
	 * 		PCBptr					Pointer to PCB in Main Memory
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		>0: 	Address of allocated block of OS memory
	 *		-2:		AddressInvalidError		Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 	
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/4/2019: Wrote InsertIntoRQ. Takes a pointer to a PCB, iterates through RQ
	 * 		to find where to insert PCBptr to maintain Priority ordering for Priority
	 * 		Round Robin CPU scheduling.
	 ****************************************************************************/
	private static long InsertIntoRQ(long PCBptr)
	{
		//Set pointers to help iterate through PCB blocks in RQ. Set curPtr to head of RQ
		long prevPtr = EOL;
		long curPtr = RQ;
		
		//Check for invalid PCB Memory Address
		if(PCBptr < 0 || PCBptr > MAXOSMEMADDRESS)
		{
			System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
			return AddressInvalidError;
		}
		
		
		MAINMEMORY[(int)(PCBptr + PCBSTATEINDEX)] = READYSTATE;	//Set state to ready state
		MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = EOL;						//Set next PCB pointer to EOL

		//Check if RQ is empty
		if(RQ == EOL)
		{
			RQ = PCBptr;
			return Success;
		}
		
		/*
		 * Iterate through RQ and find where to insert. Priority from lowest to highest
		 * is 0 to 255. Therefore, as we iterate, we compare the Priority of the PCB
		 * from PCBptr and the Priority of the PCB from curPtr. If PCBptr has a higher
		 * priorty than curPtr, we insert.
		 */
		while(curPtr != EOL)
		{
			if(MAINMEMORY[(int)(PCBptr + PCBPRIORITYINDEX)] > MAINMEMORY[(int)(curPtr + PCBPRIORITYINDEX)])
			{
				//Found where to insert
				if(prevPtr == EOL)
				{
					/*
					 * Enter PCB at the front of the list. Set the next PCB Pointer of 
					 * PCBptr to RQ, thus appending RQ to PCBptr. Then set RQ to
					 * PCBptr, which will point to the old RQ with PCBptr at the front.
					 */
					MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = RQ;
					RQ = PCBptr;
					return Success;
				}
				/*
				 * Enter PCB in the middle of the list. Set the next PCB Pointer of PCBptr
				 * to prevPtr, thus appending the rest of RQ to PCBptr. Then set prevPtr
				 * to PCBptr, which will append the front of RQ to PCBptr
				 */
				MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = MAINMEMORY[(int)(prevPtr + PCBNEXTPCBINDEX)];
				MAINMEMORY[(int)(prevPtr + PCBNEXTPCBINDEX)] = PCBptr;
				return Success;
			}
			//PCBptr to be inserted has lower or equal priority to curPtr. Go to next PCB.
			else
			{
				prevPtr = curPtr;
				curPtr = MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)];
			}	
		}
		/*
		 * While loop has ended and no place to insert was found. Insert PCB at end
		 * of the RQ
		 */
		MAINMEMORY[(int)(prevPtr + PCBNEXTPCBINDEX)] = PCBptr;
		return Success;
	}

	/*****************************************************************************
	 * Function: InsertIntoWQ
	 * 
	 * Task Description:
	 * 		Takes a parameter long PCBptr which points to an address in memory
	 * 		containing a PCB and inserts it into WQ. WQ will be unordered is not used
	 * 		for CPU scheduling so it does not to be ordered
	 * 
	 * Input Parameters:
	 * 		PCBptr					Pointer to PCB in Main Memory
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		>0: 	Address of allocated block of OS memory
	 *		-2:	AddressInvalidError		Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 	
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/6/2019: Wrote InsertIntoWQ. Takes a pointer to a PCB, checks to ensure it
	 * 		is a valid address and then inserts it at the front of the queue.
	 ****************************************************************************/
	private static long InsertIntoWQ(long PCBptr) 
	{
		if(PCBptr < 0 || PCBptr > MAXOSMEMADDRESS)
		{
			System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
			return AddressInvalidError;
		}
		MAINMEMORY[(int)(PCBptr + PCBSTATEINDEX)] = WAITINGSTATE;	//Set state to ready state
		MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = EOL;						//Set next PCB pointer to EOL
		WQ = PCBptr;
		
		return Success;
	}
	
	/*****************************************************************************
	 * Function: AllocateOSMemory
	 * 
	 * Task Description:
	 * 		Takes a parameter long requestedSize and attempts to allocate a chunk of 
	 * 		memory from OSFreeList of the appropriate size. It will return a long
	 * 		containing the address in memory of the allocated memory. This address will
	 * 		be used to initialize a PCB.
	 * 
	 * Input Parameters:
	 * 		requestedSize			Size of block we're requesting for PCB
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 			>0: 	Address of allocated block of OS memory
	 * 			-13:	NoFreeMemoryError				No free memory to allocate from list
	 * 			-14:	InvalidMemorySizeError			Invalid Memory Size. Size must be greater than 0.
	 * 	
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/2/2019: Wrote AllocateOSMemory. Takes a size, searches FreeOSMemList for
	 * 		free block of greater than or equal size. Takes the free block out of the
	 * 		list and returns the address of the allocated memory block to client code.
	 * 		Not yet test.
	 ****************************************************************************/
	private static long AllocateOSMemory(long requestedSize) 
	{
		/*
		 * OSFreeList is the pointer to the head of the list of Free OS memory. If
		 * it is equal to EOL, there is no more free space.
		 */
		if(OSFreeList == EOL) 
		{
			System.out.println("No free memory to allocate from list.");
			return NoFreeMemoryError;
		}
		/*
		 * requestedSize must be greater than 0 to be valid. Additionally,
		 * requestedSize must be atleast 2 to contain address of next block and size.
		 */
		if(requestedSize < 1) 
		{
			System.out.println("Invalid Memory Size. Size must be greater than 0.");
			return InvalidMemorySizeError;
		}
		if(requestedSize == 1)
		{
			requestedSize = 2;
		}
		/*
		 * Create pointers so that we can traverse the list of Free OS Memory. Loop
		 * through the list until we find a block of greater or equal size to
		 * requestedSize. If it is not found on curPtr, set prevPtr to curPtr and
		 * curPtr to next free block by setting it to the value contained in curPtr.
		 */
		long curPtr = OSFreeList;
		long prevPtr =  EOL;
		while(curPtr != EOL) {
			//Found a block of requestedSize
			if(MAINMEMORY[(int) curPtr + 1] == requestedSize)
			{
				
				if(curPtr == OSFreeList) //Found in first block
				{
					OSFreeList = MAINMEMORY[(int)curPtr]; //set first block of OSFreeList to the next block
					MAINMEMORY[(int)curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
				else //Found but not in first block
				{
					MAINMEMORY[(int)prevPtr] = MAINMEMORY[(int)curPtr]; //set next block of prev block to current block's next block.
					MAINMEMORY[(int)curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
			}
			//Found block with size greater than requestedSize
			else if(MAINMEMORY[(int) curPtr + 1] > requestedSize)
			{
				if(curPtr == OSFreeList) //Found in first block
				{	
					MAINMEMORY[(int) (curPtr + requestedSize)] = MAINMEMORY[(int) curPtr]; //Move address of next block to new block.
					MAINMEMORY[(int) (curPtr + requestedSize + 1)] = MAINMEMORY[(int) curPtr + 1] - requestedSize; //Calculate size of new block and use it to set new block size index
					OSFreeList = curPtr + requestedSize; //Point OSFreeList to new smaller block.
					MAINMEMORY[(int) curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
				else //Found but not in first block
				{
					MAINMEMORY[(int) (curPtr + requestedSize)] = MAINMEMORY[(int) curPtr]; //Move address of next block to new block.
					MAINMEMORY[(int) (curPtr + requestedSize + 1)] = MAINMEMORY[(int) curPtr + 1] - requestedSize; //Calculate size of new block and use it to set new block size index
					MAINMEMORY[(int) prevPtr] = curPtr + requestedSize; //Point prev block's next block to new smaller block.
					MAINMEMORY[(int) curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list		
					return curPtr; //return memory address of allocated OS block
				}
			}
			else //Current block is smaller than requestedSize
			{
				prevPtr = curPtr;
				curPtr = MAINMEMORY[(int) curPtr];
			}
		}
		/*
		 * We have not found a block that is larger than, or equal to requestedSize
		 * and we have iterated through the whole list. Therefore, this is no free
		 * OS memory
		 */
		System.out.println("No free OS memory");
		return NoFreeMemoryError;
	}
	/***************************************************************************
	 * Function: SelectProcessFromRQ
	 * 
	 * 
	 * Task:
	 * 	need to take a process from Ready Queue and place in WQ or Running Queue
	 * 
	 * Input Parameters:
	 *		None
	 *
	 * Output Parameters:
	 * 		None
	 * 
	 * Author: India Ervin (4/11)
	 **************************************************************************/
	public static long SelectProcessFromRQ()
	{
		long PCBptr = RQ;  // first entry in RQ

		if(RQ != EOL)
		{
		    // Remove first PCB from RQ
		    RQ = PCBNEXTPCBINDEX;
		}

		// Set next point to EOL in the PCB
	    PCBNEXTPCBINDEX = EOL;

		return(PCBptr);
	}  // end of SelectProcessFromRQ() function

	/*******************************************************************
	 *  Function: SaveContext
	 * 
	 * Task:
	 * 		To save CPU whenever a process in runnin in oder to 
	 * 		acess it at a later time
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Author: India Ervin
	 ***********************************************************************/ 
	public static void SaveContext(long PCBptr)
	{
		// Assume PCBptr is a valid pointer.
		long prevPtr = EOL;
		long curPtr = RQ;
		
		//Copy all CPU GPRs into PCB using PCBptr with or without using loop
		while(PCBptr != prevPtr && PCBptr != RQ) 
		{
			MAINMEMORY[(int)(PCBptr + PCBSTATEINDEX)] = SP;  	// Save SP
			MAINMEMORY[(int)(PCBptr + PCBPCBINDEX)] = PC;	// Save PC
		}
	
	
		return;
		
	}
	/*********************************************************************
	 * Function: Dispatcher
	 * 
	 * Task:
	 * 		To restore the CPU from the PCB
	 * 
	 * Input Parameter:
	 * 		PCBptr
	 * 
	 * Output paramerter:
	 * 		None
	 * 
	 * Author: India Ervin (4/11)
	 * 
	 * 
	 * 
	 ************************************************************************/
	public void Dispatcher(long PCBptr)
	{
		// PCBptr is assumed to be correct.
		
		// Copy CPU GPR register values from given PCB into the CPU registers
		// This is opposite of save CPU context
		while (PCBptr == EOL)
		{
			SP = EOL;
			PC = EOL;
		}
		// Restore SP and PC from given PCB

		// Set system mode to User mode
		long PSR = UserMode;	// UserMode is 2, OSMode is 1.

		return;
	}  // end of Dispatcher() function

	/*****************************************************************************
	 * Function: Teminate
	 * 
	 * Task:
	 * 		to stop any process and restore resources to CPU
	 * 
	 * Input Paramaters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Author: India Ervin (4/11)
	 * 
	 *****************************************************************************/
	public void TerminateProcess (long PCBptr)
	{
		// Return stack memory using stack start address and stack size in the given PCB
		MAINMEMORY[int(PCBSTACKSTARTINDEX + PCBSTACKSTATEINDEX)] = EOL;
		
		// Return PCB memory using the PCBptr
		MAINMEMORY[int(PCBptr)] = EOL;
		
		return;
	}  // end of TerminateProcess function()

	
	/*****************************************************************************
	 * Function: FreeOSMemory
	 * 
	 * Task Description:
	 * 		Takes parameters ptr and size. ptr points to a block of allocated memory.
	 * 		Size indicates the size of the block ptr is pointing to. We wish to add
	 * 		This block back into the list of Free OS Memory, thereby allowing it to be
	 * 		reallocated to another process.
	 * 
	 * Input Parameters:
	 * 		ptr						Address of start of block we are freeing
	 * 		size					Size of block we're freeing and adding back to OSFreeList
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		 0:		Success								Successful Completion
	 * 		-2:		AddressInvalidError					Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 		-13:	NoFreeMemoryError					No free memory to allocate from list
	 * 		-14:	InvalidMemorySizeError				Invalid Memory Size. Size must be greater than 0.
	 * 			
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/3/2019: Wrote FreeOSMemory. Takes the ptr to a currently allocated
	 * 		block of main memory and adds it to OSFreeList, which will allow it to
	 * 		be reallocated. Check that the ptr and size are valid, if they are
	 * 		add the block to the beginning of OSFreeList.
	 ****************************************************************************/
	private static long FreeOSMemory(long ptr, long size) 
	{
		/*
		 * Check validity of ptr. Must be within OS Memory block, check against min
		 * and max OS memory addresses. Also check that ptr + size is still within the 
		 * bounds. 
		 */
		if(ptr > MAXOSMEMADDRESS || ptr < MINOSMEMADDRESS)
		{
			System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
			return AddressInvalidError;
		}
		/*
		 * size must be greater than 0 to be valid. Additionally,
		 * size must be atleast 2 to contain address of next block and size.
		 */
		if(size == 1) 
		{
			size = 2;
		}
		else if(size < 1 || ptr + size > MAXOSMEMADDRESS)
		{
			System.out.println("Invalid Memory Size. Size must be greater than 0.");
			return InvalidMemorySizeError;
		}
		/*
		 * Return memory to OSFreeList. Insert at the beginning of the list. Set the 
		 * pointer to next block of ptr to the pointer to next block of OSFreeList,
		 * Set the size of ptr to size, set OSFreeList equal to ptr.
		 */
		MAINMEMORY[(int) ptr] = MAINMEMORY[(int) OSFreeList];
		MAINMEMORY[(int) ptr + 1] = size;
		OSFreeList = ptr;
		return Success;
	}

	/*****************************************************************************
	 * Function: AllocateUserMemory
	 * 
	 * Task Description:
	 * 		Takes a parameter long requestedSize and attempts to allocate a chunk of 
	 * 		memory from UserFreeList of the appropriate size. It will return a long
	 * 		containing the address in memory of the allocated memory.
	 * 
	 * Input Parameters:
	 * 		requestedSize			Size of block we're requesting for PCB
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 			>0: 	Address of allocated block of OS memory
	 * 			-13:	NoFreeMemoryError				No free memory to allocate from list
	 * 			-14:	InvalidMemorySizeError			Invalid Memory Size. Size must be greater than 0.
	 * 	
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/6/2019: Wrote AllocateUserMemory. Takes a size, searches UserFreeList
	 *  	for free block of greater than or equal size. Takes the free block out 
	 *  	of the list and returns the address of the allocated memory block to client 
	 *  	code. Not yet test.
	 ****************************************************************************/
	private static long AllocateUserMemory(long requestedSize) 
	{
		/*
		 * UserFreeList is the pointer to the head of the list of Free User memory. If
		 * it is equal to EOL, there is no more free space.
		 */
		if(UserFreeList == EOL) 
		{
			System.out.println("No free memory to allocate from list.");
			return NoFreeMemoryError;
		}	
		/*
		 * requestedSize must be greater than 0 to be valid. Additionally,
		 * requestedSize must be atleast 2 to contain address of next block and size.
		 */
		if(requestedSize < 1) 
		{
			System.out.println("Invalid Memory Size. Size must be greater than 0.");
			return InvalidMemorySizeError;
		}
		if(requestedSize == 1)
		{
			requestedSize = 2;
		}
		/*
		 * Create pointers so that we can traverse the list of Free User Memory. Loop
		 * through the list until we find a block of greater or equal size to
		 * requestedSize. If it is not found on curPtr, set prevPtr to curPtr and
		 * curPtr to next free block by setting it to the value contained in curPtr.
		 */
		long curPtr = UserFreeList;
		long prevPtr =  EOL;
		while(curPtr != EOL) {
			//Found a block of requestedSize
			if(MAINMEMORY[(int) curPtr + 1] == requestedSize)
			{
				
				if(curPtr == UserFreeList) //Found in first block
				{
					UserFreeList = MAINMEMORY[(int)curPtr]; //set first block of UserFreeList to the next block
					MAINMEMORY[(int)curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated User block
				}
				else //Found but not in first block
				{
					MAINMEMORY[(int)prevPtr] = MAINMEMORY[(int)curPtr]; //set next block of prev block to current block's next block.
					MAINMEMORY[(int)curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated User block
				}
			}
			//Found block with size greater than requestedSize
			else if(MAINMEMORY[(int) curPtr + 1] > requestedSize)
			{
				if(curPtr == UserFreeList) //Found in first block
				{
					MAINMEMORY[(int) (curPtr + requestedSize)] = MAINMEMORY[(int) curPtr]; //Move address of next block to new block.
					MAINMEMORY[(int) (curPtr + requestedSize + 1)] = MAINMEMORY[(int) curPtr + 1] - requestedSize; //Calculate size of new block and use it to set new block size index
					UserFreeList = curPtr + requestedSize; //Point UserFreeList to new smaller block.
					MAINMEMORY[(int) curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated User block
				}
				else //Found but not in first block
				{
					MAINMEMORY[(int) (curPtr + requestedSize)] = MAINMEMORY[(int) curPtr]; //Move address of next block to new block.
					MAINMEMORY[(int) (curPtr + requestedSize + 1)] = MAINMEMORY[(int) curPtr + 1] - requestedSize; //Calculate size of new block and use it to set new block size index
					MAINMEMORY[(int) prevPtr] = curPtr + requestedSize; //Point prev block's next block to new smaller block.
					MAINMEMORY[(int) curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated User block
				}
			}
			else //Current block is smaller than requestedSize
			{
				//Iterate to next block
				prevPtr = curPtr;
				curPtr = MAINMEMORY[(int) curPtr];
			}
		}
		/*
		 * We have not found a block that is larger than, or equal to requestedSize
		 * and we have iterated through the whole list. Therefore, this is no free
		 * OS memory
		 */
		System.out.println("No free OS memory");
		return NoFreeMemoryError;
	}
	
	/*****************************************************************************
	 * Function: FreeUserMemory
	 * 
	 * Task Description:
	 * 		Takes parameters ptr and size. ptr points to a block of allocated memory.
	 * 		Size indicates the size of the block ptr is pointing to. We wish to add
	 * 		This block back into the list of Free User Memory, thereby allowing it to be
	 * 		reallocated to another process.
	 * 
	 * Input Parameters:
	 * 		ptr						Address of start of block we are freeing
	 * 		size					Size of block we're freeing and adding back to UserFreeList
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		 0:		Success								Successful Completion
	 * 		-2:		AddressInvalidError					Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 		-13:	NoFreeMemoryError					No free memory to allocate from list
	 * 		-14:	InvalidMemorySizeError				Invalid Memory Size. Size must be greater than 0.
	 * 			
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/6/2019: Wrote FreeUserMemory. Takes the ptr to a currently allocated
	 * 		block of main memory and adds it to UserFreeList, which will allow it to
	 * 		be reallocated. Check that the ptr and size are valid, if they are
	 * 		add the block to the beginning of UserFreeList.
	 ****************************************************************************/
	private static long FreeUserMemory(long ptr, long size) 
	{
		/*
		 * Check validity of ptr. Must be within User Memory block, check against min
		 * and max User memory addresses. Also check that ptr + size is still within 
		 * the bounds. 
		 */
		if(ptr > MAXUSERMEMADDRESS || ptr < MINUSERMEMADDRESS)
		{
			System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
			return AddressInvalidError;
		}
		/*
		 * size must be greater than 0 to be valid. Additionally,
		 * size must be atleast 2 to contain address of next block and size.
		 */
		if(size == 1) 
		{
			size = 2;
		}
		else if(size < 1  || ptr + size > MAXUSERMEMADDRESS)
		{
			System.out.println("Invalid Memory Size. Size must be greater than 0.");
			return InvalidMemorySizeError;
		}
		/*
		 * Return memory to UserFreeList. Insert at the beginning of the list. Set the 
		 * pointer to next block of ptr to the pointer to next block of UserFreeList,
		 * Set the size of ptr to size, set UserFreeList equal to ptr.
		 */
		MAINMEMORY[(int) ptr] = MAINMEMORY[(int) UserFreeList];
		MAINMEMORY[(int) ptr + 1] = size;
		UserFreeList = ptr;
		return Success;
	}	
	
	/*****************************************************************************
	 * Function: SearchAndRemovePCBFromWQ
	 * 
	 * Task Description:
	 * 		Takes parameters pid and searches WQ for PCB with pid. Then returns a PCB
	 * 		pointer if it is found, otherwise, returns an Invalid PID error
	 * 
	 * Input Parameters:
	 * 		pid						pid of PCB we are searching for			
	 * 	
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		>0:		Success								Desired PCB Pointer		
	 * 		-2:		AddressInvalidError					Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 		-13:	NoFreeMemoryError					No free memory to allocate from list
	 * 		-14:	InvalidMemorySizeError				Invalid Memory Size. Size must be greater than 0.
	 * 		-15: 	InvalidPIDError						Invalid PID. Not found in queue.	
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/6/2019: Wrote SearchAndRemovePCBFromWQ. Take a pid, searches through PCBs
	 * 		in WQ and compares their pid. If they are equal, desired PCB is found,
	 * 		return PCB pointer. Otherwise, return error
	 ****************************************************************************/	
	public static long SearchAndRemovePCBFromWQ(long pid)
	{
		long curPCB = WQ;
		long prevPCB = EOL;
		
		/*
		 * Iterate through WQ. Compare curPCB's PID using PCBPIDINDEX value + curPCB
		 * and pid. If match is found, remove it from WQ and return curPCB
		 */
		while(curPCB != EOL) 
		{
			if(MAINMEMORY[(int)(curPCB + PCBPIDINDEX)] == pid)
			{
				/*
				 * Match is found, remove from WQ appropriately
				 * Case 1: It's found at the front of the list
				 * Case 2: It's found elsewhere in the list 
				 */
				if(prevPCB == EOL) //Front of WQ
				{
					WQ = MAINMEMORY[(int)(curPCB + PCBNEXTPCBINDEX)]; //Set front of WQ to the next PCB in WQ
				}
				else	//Elsewhere in WQ
				{
					MAINMEMORY[(int)(prevPCB + PCBNEXTPCBINDEX)] = MAINMEMORY[(int)(curPCB + PCBNEXTPCBINDEX)]; //Point previous PCB to next PCB, skipping over current PCB.
				}
				MAINMEMORY[(int)(curPCB + PCBNEXTPCBINDEX)] = EOL; //Remove all connections of curPCB by setting it to EOL.
				return(curPCB);
			}
			prevPCB = curPCB;
			curPCB = MAINMEMORY[(int)(curPCB + PCBNEXTPCBINDEX)];
		}
		System.out.println("Invalid PID Error. PID not found in queue");
		return InvalidPIDError;
	}

	/*****************************************************************************
	 * Function: MemAllocSystemCall
	 * 
	 * Task Description:
	 * 		Allocates memory from user free list. GPR 2 has the size of the memory
	 * 		to be allocated, GPR 1 will be set to the address returned by
	 * 		AllocateUserMemory method.
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		>0:		Success								Address of allocated memory
	 * 		-2:		AddressInvalidError					Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 *		-13:	NoFreeMemoryError					No free memory to allocate from list			
	 *		-14:	InvalidMemorySizeError				Invalid Memory Size. Size must be greater than 0
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		4/4/2019: Wrote MemAllocSystemCall method. Not yet tested.
	 *****************************************************************************/
	public static long MemAllocSystemCall() 
	{
		//Declare and initialize size to value in GPR 2
		long size = GPRS[2];
		/*
		 * If size is greater than the maximum size allowed (the difference of the max
		 * user address and the min user address), then the size is too large.
		 * Otherwise, AllocateUserMemory will also return an error if the size requested
		 * is too large for any free block to accommodate. GPR 0 contains the return
		 * status, set GPR 0 to InvalidMemorySizeError, if applicable, and return it.
		 */
		if(size > (MAXUSERMEMADDRESS - MINUSERMEMADDRESS))
		{
			GPRS[0] = InvalidMemorySizeError;
			return GPRS[0];
		}
		// If size is 1, change it to 2 because PCB requires a block for address and size
		else if(size == 1)
		{
			size = 2;
		}
		/*
 		 * Set GPR 1 to address allocated from User Free Block, by AllocateUserMemory
 		 * method. If AllocateUserMemory method returns an error code, notated by a
 		 * negative number, set GPR 0 to that error code, otherwise, GPR 0 is OK status 0
		 */
		GPRS[1] = AllocateUserMemory(size);
		if(GPRS[1] < 0)
		{
			GPRS[0] = GPRS[1];
		}
		else
		{
			GPRS[0] = Success;
		}
		
		System.out.println("Memory Allocation System Call:" + 
		"\nGPR0:\t" + GPRS[0] +
		"\nGPR1:\t" + GPRS[1] +
		"\nGPR2:\t" + GPRS[2]
		);
		return GPRS[0];
	}
	
	/*****************************************************************************
	 * Function: MemFreeSystemCall
	 * 
	 * Task Description:
	 * 		Returns dynamically allocated user memory to user free list. GPR 1 has
	 * 		memory addresses and GPR 2 has the memory size to be released.
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		>0:		Success								Address of allocated memory
	 *		-2:		AddressInvalidError					Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999		
	 *		-13:	NoFreeMemoryError					No free memory to allocate from list			
	 *		-14:	InvalidMemorySizeError				Invalid Memory Size. Size must be greater than 0
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		4/4/2019: Wrote MemFreeSystemCall method. Not yet tested.
	 *****************************************************************************/	
	public static long MemFreeSystemCall() 
	{
		//Declare and initialize size to value in GPR 2
		long size = GPRS[2];
		/*
		 * If size is greater than the maximum size allowed (the difference of the max
		 * user address and the min user address), then the size is too large.
		 * Otherwise, FreeUserMemory will also return an error if the size requested
		 * is too large for any free block to accommodate. GPR 0 contains the return
		 * status, set GPR 0 to InvalidMemorySizeError, if applicable, and return it.
		 */
		if(size > (MAXUSERMEMADDRESS - MINUSERMEMADDRESS))
		{
			GPRS[0] = InvalidMemorySizeError;
			return GPRS[0];
		}
		// If size is 1, change it to 2 because PCB requires a block for address and size
		else if(size == 1)
		{
			size = 2;
		}
		/*
 		 * Set GPR 1 to address allocated from User Free Block, by AllocateUserMemory
 		 * method. If FreeUserMemory method returns an error code, notated by a
 		 * negative number, set GPR 0 to that error code, otherwise, GPR 0 is OK status 0
		 */
		GPRS[0] = FreeUserMemory(GPRS[1] ,size);
		
		System.out.println("Memory Free System Call:" + 
		"\nGPR0:\t" + GPRS[0] +
		"\nGPR1:\t" + GPRS[1] +
		"\nGPR2:\t" + GPRS[2]
		);
		return GPRS[0];
	}	

	/*****************************************************************************
	 * Function: CreateProcess
	 * 
	 * Task Description:
	 * 		Takes the filename of a program, allocates OS memory for a PCB, allocates
	 * 		User memory for program space. Initializes its PCB and gives it the parameter
	 * 		priority. It produces a MemoryDump for the Process created and then inserts
	 * 		it into Ready Queue.
	 * 
	 * Input Parameters:
	 * 		filename			Filename of program we will load into main memory
	 * 		priority			Priority we wish to apply to the PCB
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 			>0: 	Success	
	 * 			-1:		FileOpenError					Unable to open the file
	 *			-2:		AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 *			-3:		InvalidPCValueError				Invalid PC value. Value must be between 0 and 9999
	 *			-4: 	NoEndOfProgramError				Missing end of program indicator
	 * 			-13:	NoFreeMemoryError				No free memory to allocate from list
	 * 			-14:	InvalidMemorySizeError			Invalid Memory Size. Size must be greater than 0.
	 * 
	 * Author: India Ervin
	 * Change Log:
	 * 		4/11/2019: Wrote CreateProcess method.
	 * 		4/15/2019: Revised By Jonathon Ku
	 *****************************************************************************/
	public static long CreateProcess(String filename, long priority) 
	{
		//Allocate space for PCB
		long PCBptr = AllocateOSMemory(PCBSIZE);
		//Check for error, represented with value < 0. Return error code
		if(PCBptr < 0) 
		{
			return PCBptr;
		}
		
		//Initialize PCB
		InitializePCB(PCBptr);
		
		//Load program
		long value = AbsoluteLoader(filename);
		//Check for errors
		if(value < 0) 
		{
			return value;
		}
		MAINMEMORY[(int)(PCBptr + PCBPCINDEX)] = value;	
		
		//Allocate stack space from UserFreeList
		long ptr = AllocateUserMemory(PCBSTACKSIZE);
		//Check for errors
		if(ptr < 0)
		{
			//User memory allocation has failed, so we must free the allocated OS memory
			FreeOSMemory(PCBptr, PCBSIZE);
			return ptr;
		}
		
		//Store stack information in PCB (SP, ptr, and size)
		MAINMEMORY[(int)(PCBptr + PCBSPINDEX)] = ptr; //empty stack is low address, full is high address
		MAINMEMORY[(int)(PCBptr + PCBSTACKSTARTINDEX)] = ptr; 
		MAINMEMORY[(int)(PCBptr + PCBSTACKSIZEINDEX)] = PCBSTACKSIZE; 
		
		//Set priority of PCB
		MAINMEMORY[(int)(PCBptr + PCBPRIORITYINDEX)] = priority;
		
		//Dump Memory
		DumpMemory("Dumping Process: PID " + MAINMEMORY[(int)(PCBptr + PCBPIDINDEX)], MINPROGRAMADDRESS, MAXPROGRAMADDRESS - MINPROGRAMADDRESS + 1);
		
		//Print PCB
		PrintPCB(PCBptr);
		
		//Insert PCB into RQ. No need to check for errors as the address was given by 
		//AllocateOSMemory method, so address will be valid.
		InsertIntoRQ(PCBptr);
		
		return(Success);
	}	
	
	/*****************************************************************************
	 * Function: SelectProcessFromRQ
	 * 
	 * Task Description:
	 * 		Returns the first PCB in Ready Queue
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 *			>0: 	Success							Address of First PCB in RQ	
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		4/11/2019: Wrote SelectProcessFromRQ method.
	 *****************************************************************************/	
	public static long SelectProcessFromRQ() 
	{
		long PCBptr = RQ;
		//Do we need to make an error for no PCB in RQ?
		if(RQ != EOL)
		{
			//Remove first PCB from RQ
			RQ = MAINMEMORY[(int)RQ];
		}
		
		//Set next PCB of PCB to EOL
		MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = EOL;
		
		return PCBptr;
	}

	/*****************************************************************************
	 * Function: SaveContext
	 * 
	 * Task Description:
	 * 		stores GPR values, SP and PC to PCB.
	 * 
	 * Input Parameters:
	 * 		PCBptr				Points to the PCB we wish to save
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value: 
	 *		None
	 *
	 * Author: India Ervin
	 * Change Log:
	 * 		4/11/2019: Wrote SaveContext method.
	 * 		4/15/2019: Revised By Jonathon Ku	
	 *****************************************************************************/		
	public static void SaveContext(long PCBptr)
	{
		//Assume PCBptr is a valid pointer.
		//Copy contents of GPRS into PCB
		MAINMEMORY[(int)(PCBptr + PCBGPR0)] = GPRS[0];
		MAINMEMORY[(int)(PCBptr + PCBGPR1)] = GPRS[1];
		MAINMEMORY[(int)(PCBptr + PCBGPR2)] = GPRS[2];
		MAINMEMORY[(int)(PCBptr + PCBGPR3)] = GPRS[3];
		MAINMEMORY[(int)(PCBptr + PCBGPR4)] = GPRS[4];
		MAINMEMORY[(int)(PCBptr + PCBGPR5)] = GPRS[5];
		MAINMEMORY[(int)(PCBptr + PCBGPR6)] = GPRS[6];
		MAINMEMORY[(int)(PCBptr + PCBGPR7)] = GPRS[7];
		
		//Copy SP and PC into PCB
		MAINMEMORY[(int)(PCBptr + PCBSPINDEX)] = SP;
		MAINMEMORY[(int)(PCBptr + PCBPCINDEX)] = PC;
		
		return;
	}

	/*****************************************************************************
	 * Function: Dispatcher
	 * 
	 * Task Description:
	 * 		restores CPU context from PCB into CPU registers. This is used when a
	 * 		process is given the CPU once again.
	 * 
	 * Input Parameters:
	 * 		PCBptr				Points to the PCB we wish to load
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value: 
	 *		None
	 * Author: India Ervin
	 * Change Log:
	 * 		4/11/2019: Wrote Dispatcher method.
	 * 	 	4/15/2019: Revised By Jonathon Ku
	 *****************************************************************************/		
	public static void Dispatcher(long PCBptr)
	{
		//PCBptr is assumed to be correct
		//Copy CPU GPR register values from PCB into CPU registers
		GPRS[0] = MAINMEMORY[(int)(PCBptr + PCBGPR0)];
		GPRS[1] = MAINMEMORY[(int)(PCBptr + PCBGPR1)];
		GPRS[2] = MAINMEMORY[(int)(PCBptr + PCBGPR2)];
		GPRS[3] = MAINMEMORY[(int)(PCBptr + PCBGPR3)];
		GPRS[4] = MAINMEMORY[(int)(PCBptr + PCBGPR4)];
		GPRS[5] = MAINMEMORY[(int)(PCBptr + PCBGPR5)];
		GPRS[6] = MAINMEMORY[(int)(PCBptr + PCBGPR6)];
		GPRS[7] = MAINMEMORY[(int)(PCBptr + PCBGPR7)];
		
		//Copy SP and PC from PCB
		SP = MAINMEMORY[(int)(PCBptr + PCBSPINDEX)];
		PC = MAINMEMORY[(int)(PCBptr + PCBPCINDEX)];
		
		//Set system mode to User Mode
		PSR = UserMode;
		
		return;
	}

	/*****************************************************************************
	 * Function: TerminateProcess
	 * 
	 * Task Description:
	 * 		Free allocated memory to Process back to OSFreeList and UserFreeList		
	 * 
	 * Input Parameters:
	 * 		PCBptr				Points to the PCB we wish to terminate
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value: 
	 *		None
	 * Author: India Ervin
	 * 
	 * Change Log:
	 * 		4/11/2019: Wrote TerminateProcess method.
	 * 		4/15/2019: Revised By Jonathon Ku
	 *****************************************************************************/	
	public static void TerminateProcess(long PCBptr)
	{
		//Return User Memory using the stack address and size in PCB
		FreeUserMemory(MAINMEMORY[(int)(PCBptr + PCBSTACKSTARTINDEX)], MAINMEMORY[(int)(PCBptr + PCBSTACKSIZEINDEX)]);
		
		//Return OS memory using the PCBptr and constant PCBSize
		FreeOSMemory(PCBptr, PCBSIZE);
		
		return;
	}
	
	/*
	// Function: 
	// InitilizePCB
	// 
	// Task description:
	// Initialize PCB and next 21 values to 0 to allocate space for it
	// Set PID, default priority, and state to ready
	// 
	// Input:
	// PCBPtr 
	// 
	// Function Return Value:
	// None
	// 
	// Author:
	// Gabe Freitas
	*/
	private static void InitializePCB(long PCBPtr)
	{
		//Iterate through PCB array to make values equal to 0
		for(int pcbIndex = 2; pcbIndex <= PCBSIZE; pcbIndex++)
		{
			MAINMEMORY[(int)(PCBPtr + pcbIndex)] = 0;
		}

		//Set defaults of PCB 
		MAINMEMORY[(int)(PCBPtr + PCBPIDINDEX)] = PID++;
		MAINMEMORY[(int)(PCBPtr + PCBPRIORITYINDEX)] = DEFAULTPRIORITY;
		MAINMEMORY[(int)(PCBPtr + PCBSTATEINDEX)] = READYSTATE; 
		MAINMEMORY[(int)(PCBPtr + PCBNEXTPCBINDEX)] = EOL;

		return;
	}
	
	/*
	// Function: 
	// CheckAndProcessInterrupt
	// 
	// Task description:
	// Prompt user to enter a interrupt ID and enter the appropriate method
	// if invalid input, tell user
	// 
	// Input:
	// None 
	// 
	// Return Value:
	// None
	// 
	// Author:
	// Gabe Freitas
	*/
	private static long CheckAndProcessInterrupt()
	{
		Scanner userIn = new Scanner(System.in);

		long status = Success;

		//Print out the possible inputs allowed by user and what they do
		System.out.println("Possible interrupt IDs:");
		System.out.println("0 - No Interrupt");
		System.out.println("1 - Run Program");
		System.out.println("2 - Shutdown System");
		System.out.println("3 - Input Operation Completion");
		System.out.println("4 - Output Operation Completion");
		System.out.println("Please enter the interrupt ID: ");
		int interruptID = userIn.nextInt();
		System.out.println("Interrupt ID inputted: " + interruptID);

		//Switch case to handle user input 
		switch(interruptID)
		{
			case 0:
				break;

			case 1:
				ISRRunProgramInterrupt();
				break;

 			case 2:
				ISRShutdownSystem();
				status = ShutdownError; 
				break;

 			case 3: 
				ISRInputCompletionInterrupt();
				break;

 			case 4:
				ISROutputCompletionInterrupt();
				break;

 			default:
				System.out.println("Invalid interrupt ID");
				break;
		}//end of InterruptIDSwitch
		return status;
	}//End of CheckAndProcessInterrupt() function

	/*
	// Function: 
	// ISRRunProgramInterrupt
	// 
	// Task description:
	// Prompt and create process based on user input
	// 
	// Input:
	// None 
	// 
	// Return Value:
	// None
	// 
	// Author:
	// Gabe Freitas
	*/
	static void ISRRunProgramInterrupt()
	{
		Scanner userIn = new Scanner(System.in);
		System.out.println("Please enter the filename: ");
		String fileName = userIn.nextLine();

 		CreateProcess(fileName, DEFAULTPRIORITY);
		return;
	}

	/*
	// Function: 
	// ISRInputCompletionInterrupt
	// 
	// Task description:
	// Prompt user for the PID of the process, search WQ and RQ for process 
	// Remove from WQ and insert into RQ if it is in RQ
	// Prompt user for a character and print it
	// 
	// Input:
	// None 
	// 
	// Return Value:
	// None
	// 
	// Author:
	// Gabe Freitas
	*/
 	static void ISRInputCompletionInterrupt()
	{
		
		Scanner userIn = new Scanner(System.in);
		System.out.println("Please enter the PID of the process:");
		long desiredPID = userIn.nextInt();
		long prevPCB = EOL; //
		long rqPCB = RQ; //Start at head of RQ
		long wqPCB = WQ; //start at head of WQ

		//if  wqPCB == EOL, move into checking through RQ
		//iterate through WQ
		while(wqPCB != EOL) 
		{
			if(MAINMEMORY[(int)(wqPCB + PCBPIDINDEX)] == desiredPID)
			{
				//If condition is met, remove PID from WQ, store character into PCB GPR 0, and add PCB to RQ
				SearchAndRemovePCBFromWQ(desiredPID);
				System.out.println("Please enter a character (A-Z): ");
				char userChar = userIn.next().charAt(0);
				MAINMEMORY[(int)(desiredPID + PCBGPR0)] = userChar;
				MAINMEMORY[(int)(desiredPID + PCBSTATEINDEX)] = READYSTATE;
				InsertIntoRQ(desiredPID);
				return;
			}

			prevPCB = desiredPID;
			wqPCB = MAINMEMORY[(int)(wqPCB + PCBNEXTPCBINDEX)]; //Iterate through WQ
		}


		//Search through RQ until EOL is reached
		while(rqPCB != EOL)
		{
			//If PID is found in RQ, store entered character in gpr 0
			if(MAINMEMORY[(int)(rqPCB + PCBPIDINDEX)] == desiredPID)
			{
				System.out.println("Please enter a character (A-Z): ");
				char userChar = userIn.next().charAt(0);
				MAINMEMORY[(int)(desiredPID + PCBGPR0)] = userChar;
			}

			//Store previous PCB 
			prevPCB = desiredPID;
			//Continue to iterate through RQ
			rqPCB = MAINMEMORY[(int)(rqPCB + PCBNEXTPCBINDEX)];
		}

		//Inform user PID is invalid
		System.out.println("Invalid PID: " + desiredPID +  ", please enter valid PID");
		;
		return;
	 } //End of ISRinputCompletionInterrupt() function
	 
	/*
	// Function: 
	// ISROutputCompletionInterrupt
	// 
	// Task description:
	// Prompt user for the PID of the process, search WQ and RQ for process 
	// Remove from WQ and insert into RQ if it is in RQ
	// Print character in GPR0
	// 
	// Input:
	// None 
	// 
	// Return Value:
	// None
	// 
	// Author:
	// Gabe Freitas
	*/
	 static void ISROutputCompletionInterrupt()
	{
		Scanner userIn = new Scanner(System.in);
		System.out.println("Please enter the PID: ");
		long desiredPID = userIn.nextInt();
		long prevPCB = EOL;
		long wqPCB = WQ; //start at head of WQ
		long rqPCB = RQ; //start at head of RQ

		//if  wqPCB == EOL, move into checking through RQ
		//iterate through WQ
		while(wqPCB != EOL) 
		{
			if(MAINMEMORY[(int)(wqPCB + PCBPIDINDEX)] == desiredPID)
			{
				//Remove PCB from WQ and print character in PCB GPR 0
				SearchAndRemovePCBFromWQ(desiredPID);
				System.out.println(MAINMEMORY[(int)(wqPCB + PCBGPR0)]);
				MAINMEMORY[(int)(wqPCB + PCBSTATEINDEX)] = READYSTATE;
				InsertIntoRQ(wqPCB); //Insert PCB into RQ
				return;
			}
			prevPCB = wqPCB;
			//Iterate through WQ
			wqPCB = MAINMEMORY[(int)(wqPCB + PCBNEXTPCBINDEX)];
		}

		//Search RQ for desiredPID
		while(rqPCB != EOL)
		{
			if(MAINMEMORY[(int)(rqPCB + PCBPIDINDEX)] == desiredPID)
			{
				//Print whatever is in PCB GPR0
				System.out.println(MAINMEMORY[(int)(rqPCB + PCBGPR0)]);
				return;
			}

			prevPCB = rqPCB;
			//Iterate through RQPCB
			rqPCB = MAINMEMORY[(int)(rqPCB + PCBNEXTPCBINDEX)];
		}
		;
		//Inform user inputted PID is invalid
		System.out.println("Invalid PID: " + desiredPID +  ", please enter valid PID");
		return;
	}  // end of ISROutputCompletionInterrupt() function

	/*
	// Function: 
	// ISRShutdownSystem
	// 
	// Task description:
	// Terminate processes in both RQ and WQ by iterating through them
	// 
	// Input:
	// None 
	// 
	// Return Value:
	// None
	// 
	// Author:
	// Gabe Freitas 
	*/
	static void ISRShutdownSystem()
	{
		 int ptr = (int)RQ;

		 //Iterate through RQ looking for next process to terminate
		 while(ptr != EOL)
		 {
			RQ = MAINMEMORY[(int)(ptr + PCBNEXTPCBINDEX)];
			//Set RQ to next process in RQ
			//Terminate process using rq ptr
			TerminateProcess(ptr); 
			//Set ptr to next value in RQ
			ptr = (int)RQ;
		 }

		ptr = (int)WQ;

		//Iterate through RQ
		while(ptr != EOL)
		{
			WQ = MAINMEMORY[(int)(ptr+ PCBNEXTPCBINDEX)];
			//Set WQ to next process in RQ
			//Terminate process using wq ptr
			TerminateProcess(ptr);
			//Set ptr to next value in WQ
			ptr = (int)WQ;
		}
		return;
	} // end of ISRShutdownSystem() function

	/*
	// Function: 
	// SystemCall
	// 
	// Task description:
	// Have PSR enter OSMode and enter appropriate case for SysCallID handling
	// 
	// Input:
	// Long SystemCallID - SysCallID passed to OS 
	// 
	// Return Value:
	// Status - Status telling if operation was successful
	// 
	// Author:
	// Gabe Freitas
	*/
	static long SystemCall(long SystemCallID)
	{
		//Enter OsMode
		PSR = OSMode;

		//Default to OK status
		long status = Success;

		//Switch for handling SysCallId
		switch((int)SystemCallID)
		{
			case 1:
				System.out.println("Create process system call not implemented");
				break;
			case 2:
				System.out.println("Delete process system call not implemented");
				break;
			case 3:
				System.out.println("Process inquiry system call not implemented");
				break;
			case 4:
				status = MemAllocSystemCall();
				break;
			case 5:
				status = MemFreeSystemCall();
				break;
			case 6:
				System.out.println("Message send system call not implemented");
				break;
			case 7:
				System.out.println("Message receive system call not implemented");
				break;
			case 8:
				status = io_getcSystemCall(); 
				break;
			case 9:
				status = io_putcSystemCall();
				break;
			case 10:
				System.out.println("Time get system call not implemented");
				break;
			case 11:
				System.out.println("Time set system call not implemented");
				break;
			default:
				System.out.println("Invalid system call ID"); //User entered invalid SysCall
				status = InvalidSystemCallID; //Return error code
				break;
		}
		//Return Os back to user
		PSR = UserMode;
		return status;
	} //End of SystemCall() Function

	/* Function: PrintPCB
	 * 
	 * Task Description:
	 * 		printing out all the values in a PCB process
	 * 
	 * Input:
	 * 		PCBptr: Address of PCB to be printed
	 * 
	 * Output:
	 * 		None
	 * 
	 * Author: India Ervin 4/11
	 * Revised by: Gabe Freitas 4/15
	 * 
	 * 
	 */
	public static void PrintPCB(long PCBptr)
	{
		/*Print the values of the following fields from PCB with a text before the value like below:
		*	PCB address = 6000, Next PCB Ptr = 5000, PID = 2, State = 2, PC = 200, SP = 4000, 
		*	Priority = 127, Stack info: start address = 3990, size = 10
		*	GPRs = print 8 values of GPR 0 to GPR 7
		*/
		
		/*
		 * Use of a StringBuilder to account for the many values needed
		 * to print the PCB
		 */
		StringBuilder pcbFormat = new StringBuilder("PCB Address: ");
				pcbFormat.append(PCBptr + ", ");
				pcbFormat.append("Next PCB Pointer: " + MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] + ", ");
				pcbFormat.append("PID: " + MAINMEMORY[(int)(PCBptr + PCBPIDINDEX)]+ ", ");
				pcbFormat.append("PC: "  + MAINMEMORY[(int)(PCBptr + PCBPCINDEX)] + ", ");
				pcbFormat.append("SP: " + MAINMEMORY[(int)(PCBptr + PCBSPINDEX)] + "\n");
				pcbFormat.append("Priority = " + MAINMEMORY[(int)(PCBptr + PCBPRIORITYINDEX)] + ", ");
				pcbFormat.append("Stack info: Start address: " + MAINMEMORY[(int)(PCBptr + PCBSTACKSTARTINDEX)] + ", ");
				pcbFormat.append("Size:  " + MAINMEMORY[(int)(PCBptr + PCBSTACKSIZEINDEX)] + "\n");
				pcbFormat.append("GPRs: " + MAINMEMORY[(int)(PCBptr + PCBGPR0)] + ", ");
				pcbFormat.append(MAINMEMORY[(int)(PCBptr + PCBGPR0)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR1)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR2)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR3)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR4)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR5)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR6)] + ", "  + MAINMEMORY[(int)(PCBptr + PCBGPR7)] + "\n");
				
				System.out.print(pcbFormat.toString());
	}  // end of PrintPCB() function

	public static long io_getcSystemCall()
	{
		return STARTOFOUTPUTEVENT;
	}

	public static long io_putcSystemCall()
	{
		return STARTOFINPUTEVENT;
	}
	
	/*****************************************************************************
	 * Function: Main
	 * 
	 * Task Description:
	 * 		Takes user input and calls other methods to execute them.
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		None
	 * 
	 * Author: Jonathon Ku & Gabe Freitas
	 * Change Log:
	 * 		2/8/2019: Created scanner to read user input. Prompts user to enter a file
	 * 		to open. Then it calls the AbsoluteLoader using the file they enter. If
	 * 		AbsoluteLoader returns an error code, represented by a value less than 0,
	 * 		end program using return.
	 * 
	 *****************************************************************************/
	public static void main(String[] args) 
	{
		//Initialize all global variables to 0.
		InitializeSystem(); 
		long status = Success; 
		while(status != ShutdownError)
		{
			status = CheckAndProcessInterrupt();
			
			if(status != Success)
			{
				return;
			}
<<<<<<< HEAD

			System.out.println("RQ Before CPU Scheduling: ");
			long iterator = RQ;
			while(iterator != EOL)
			{
				System.out.println(iterator);
				iterator = MAINMEMORY[(int)iterator];
			}
			System.out.println();
			
			System.out.println("WQ Before CPU Scheduling: ");
			iterator = WQ;
			while(iterator != EOL)
			{
				System.out.println(MAINMEMORY[(int)iterator]);
				iterator = MAINMEMORY[(int)iterator];
			}
			System.out.println();
			
			long runningPCBPtr = SelectProcessFromRQ();

			Dispatcher(runningPCBPtr);

			status = ExecuteProgram();

=======

			System.out.println("RQ Before CPU Scheduling: ");
			long iterator = RQ;
			while(iterator != EOL)
			{
				System.out.println(iterator);
				iterator = MAINMEMORY[(int)iterator];
			}
			System.out.println();
			
			System.out.println("WQ Before CPU Scheduling: ");
			iterator = WQ;
			while(iterator != EOL)
			{
				System.out.println(MAINMEMORY[(int)iterator]);
				iterator = MAINMEMORY[(int)iterator];
			}
			System.out.println();
			
			long runningPCBPtr = SelectProcessFromRQ();

			Dispatcher(runningPCBPtr);

			status = ExecuteProgram();

>>>>>>> 58d78f29b36856a0746d6af1a2c5dc07b7af0d15
			DumpMemory("Dynamic Memory Area before CPU scheduling", MINUSERMEMADDRESS, MAXUSERMEMADDRESS - MINUSERMEMADDRESS + 1);

			if(status == TimeSliceExpiredError)
			{
				SaveContext(runningPCBPtr);
				InsertIntoRQ(runningPCBPtr);
				runningPCBPtr = EOL;
			}
			else if(status <= 0)
			{
				TerminateProcess(runningPCBPtr);
				runningPCBPtr = EOL;
			}
			else if(status == STARTOFINPUTEVENT)
			{
				MAINMEMORY[(int)(runningPCBPtr + PCBREASONFORWAITINGINDEX)] = STARTOFINPUTEVENT;
				InsertIntoWQ(runningPCBPtr);
				runningPCBPtr = EOL;
			}
			else if (status == STARTOFOUTPUTEVENT)
			{
				MAINMEMORY[(int)(runningPCBPtr + PCBREASONFORWAITINGINDEX)] = STARTOFOUTPUTEVENT;
				InsertIntoWQ(runningPCBPtr);
				runningPCBPtr = EOL;
			}
			else
			{
				System.out.println("Unknown error message");
			}
		}

		System.out.println("Operating system will now shut down");
		return;
	}
}

/******************************************************************************
 * Local Class: Operand
 * 
 * Purpose: This local class allows multiple variables to be returned to the
 * 			calling function. It only contains a constructor and get methods
 * 			and does not need to error trap as it is taken care of when the
 *			constructor is called.
 *
 *
 ******************************************************************************/
class Operand
{
	//Local Variables
	private long status;
	private long address;
	private long value;
	
	//Constructor
	public Operand(long status, long address, long value)
	{
		this.status = status;
		this.address = address;
		this.value = value;
	}
	
	//Get Methods
	public long getStatus()
	{
		return status;
	}
	public long getAddress()
	{
		return address;
	}
	public long getValue()
	{
		return value;
	}
}