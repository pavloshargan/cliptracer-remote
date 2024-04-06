import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.time.DateTimeException

object TimeProvider {
    fun getUTCTimeMilliseconds(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getUTCTimeMillisecondsApi33AndAbove()
        } else {
            // For API lower than 33, use the regular system time
            println("Inaccurate time")
            System.currentTimeMillis()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getUTCTimeMillisecondsApi33AndAbove(): Long {
        return try {
//            println("Precise network time")
            SystemClock.currentNetworkTimeClock().millis()

        } catch (e: DateTimeException) {
            try {
                println("Precise gnss time")
                SystemClock.currentGnssTimeClock().millis()
            } catch (e: DateTimeException) {
                // Default to system time if both GNSS and network times are unavailable
                println("Inaccurate time (exception)")
                System.currentTimeMillis()
            }
        }
    }
}
