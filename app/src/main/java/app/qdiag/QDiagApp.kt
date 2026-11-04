package app.qdiag

import android.app.Application
import app.qdiag.diag.Diag
import app.qdiag.diag.Reports

class QDiagApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Diag.start(this, Graph.http, Graph.scope) { Graph.settings.current.sendDiagnostics }
        Reports.start(this, Graph.http, Graph.scope) { Graph.settings.current.sendReports }
    }
}
