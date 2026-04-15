import com.google.ai.edge.litertlm.Backend
fun test() {
    val b1 = Backend.CPU()
    val b2 = Backend.GPU()
    val b3 = Backend.NPU()
}
