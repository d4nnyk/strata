package denali

import java.util.concurrent.{Future, Callable, Executors, ExecutorCompletionService}

import denali.data.{InstructionFile, State, Instruction}
import denali.tasks._
import denali.util.IO
import denali.util.ColoredOutput._

import scala.util.Random

/**
 * Driver for a full run of denali.  Takes care of deciding what to run next.
 */
class Driver(val globalOptions: GlobalOptions) {

  val state = State(globalOptions)

  def run(args: Array[String]): Unit = {
    // initialize
    Initialize.run(args, InitOptions(globalOptions))

    // TODO better value
    val nThreads = Runtime.getRuntime.availableProcessors() / 2
    IO.info(s"Running with $nThreads threads")

    // our pool of threads to do all the computation
    val executor = Executors.newFixedThreadPool(nThreads)
    val threadPool = new ExecutorCompletionService[TaskResult](executor)
    var tasksRunning = 0
    val taskMap = collection.mutable.HashMap[Future[TaskResult], Task]()

    /** start a new task */
    def startNewTask(task: Task): Unit = {
      val callable: Callable[TaskResult] = new Callable[TaskResult] {
        def call(): TaskResult = {
          runTask(task)
        }
      }
      tasksRunning += 1
      val future = threadPool.submit(callable)
      taskMap.put(future, task)
    }

    /** Start up to n new tasks. */
    def startNewTasks(n: Int): Unit = {
      for (i <- 1 to n) {
        selectNextTask() match {
          case None =>
          case Some(t) => startNewTask(t)
        }
      }
    }

    // initial set of tasks
    startNewTasks(nThreads)

    // main loop
    while (tasksRunning > 0) {
      val task = threadPool.take()
      try {
        val taskRes = task.get()
        // TODO handle result
        handleTaskResult(taskRes)
      } catch {
        case t: Throwable =>
          // TODO error handling
          state.appendLogUnexpected(s"exception: ${t.getMessage}\n${t.getStackTrace.mkString("\n")}")
          IO.info(s"ERROR: failure: ${t.getMessage}\n${t.getStackTrace.mkString("\n")}".red)
          state.lockInformation()
          state.removeInstructionToFile(taskMap(task).instruction, InstructionFile.Worklist)
          state.unlockInformation()
      }
      tasksRunning -= 1

      // start new tasks
      startNewTasks(nThreads - tasksRunning)
    }

    executor.shutdown()
    IO.info("Finished all tasks")
  }

  /** Execute a task. */
  def runTask(task: Task): TaskResult = {
    task match {
      case t: InitialSearchTask =>
        InitialSearch.run(t)
    }
  }

  /** Handle the result of a task. */
  def handleTaskResult(taskRes: TaskResult): Unit = {
    state.lockInformation()

    try {
      state.removeInstructionToFile(taskRes.instruction, InstructionFile.Worklist)
      taskRes match {
        case InitialSearchSuccess(task) =>
          // TODO: actually this is only partial success
          state.addInstructionToFile(taskRes.instruction, InstructionFile.Success)
          state.removeInstructionToFile(taskRes.instruction, InstructionFile.RemainingGoal)
          IO.info(s"initial search success for ${task.instruction}")
        case InitialSearchTimeout(task) =>
          IO.info(s"initial search timeout for ${task.instruction}")
      }
    } finally {
      state.unlockInformation()
    }
  }

  /** Select what next step should be done, and puts the task into the worklist. */
  def selectNextTask(instruction: Option[Instruction] = None): Option[Task] = {
    state.lockInformation()

    def mkInitialSearch(instr: Instruction): Option[Task] = {
      // TODO correct budget
      val budget = InitialSearch.computeBudget(state, instr)
      state.addInstructionToFile(instr, InstructionFile.Worklist)
      Some(InitialSearchTask(state.globalOptions, instr, budget))
    }

    try {
      val goal = state.getInstructionFile(InstructionFile.RemainingGoal)
      val partial_succ = state.getInstructionFile(InstructionFile.PartialSuccess)

      if (instruction.isDefined) {
        val instr = instruction.get
        if (goal.contains(instr)) {
          return mkInitialSearch(instr)
        } else {
          // TODO secondary search
          return None
        }
      }

      // first deal with partial successes (so that we can put them into success as soon as possible)
      if (partial_succ.nonEmpty) {
        // TODO secondary search
        return Some(null)
      }

      // then try an intial search
      if (goal.nonEmpty) {
        val instr = goal(Random.nextInt(goal.size))
        return mkInitialSearch(instr)
      }

      // cannot do anything for now
      None
    } finally {
      state.unlockInformation()
    }
  }
}

/**
 * Companion object
 */
object Driver {
  def apply(globalOptions: GlobalOptions) = new Driver(globalOptions)
}