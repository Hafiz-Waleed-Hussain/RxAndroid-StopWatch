package com.uwantolearn.rxandroidstopwatch

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

class StopWatchViewModel : ViewModel() {


    private val clicksHandlerSubject = PublishSubject.create<StopWatch>()
    private val uiStateSubject = PublishSubject.create<String>()
    private val buttonStateSubject = BehaviorSubject.create<Triple<Boolean, Boolean, Boolean>>()
    // Start      Reset        Pause
    private val startState: Triple<Boolean, Boolean, Boolean> = Triple(false, true, true)
    private val resetState: Triple<Boolean, Boolean, Boolean> = Triple(true, false, false)
    private val pauseState: Triple<Boolean, Boolean, Boolean> = Triple(true, true, false)


    private val timerFormatter: (Long) -> String =
        { seconds -> "${seconds / 60} : ${seconds % 60}" }

    private val pauseSubject = BehaviorSubject.createDefault(0L)
    private val resumeSubject = BehaviorSubject.createDefault(0L)

    init {
        clicksHandlerSubject.switchMap {
            when (it) {
                StopWatch.Start -> {
                    buttonStateSubject.onNext(startState)
                    timerObservable().doOnNext(pauseSubject::onNext)
                }
                StopWatch.Reset -> {
                    buttonStateSubject.onNext(resetState)
                    Observable.just(0L)
                        .doOnNext(pauseSubject::onNext)
                        .doOnNext(resumeSubject::onNext)
                }
                StopWatch.Pause -> {
                    buttonStateSubject.onNext(pauseState)
                    resumeSubject.onNext(pauseSubject.value!!)
                    Observable.never()
                }
            }
        }.map(timerFormatter)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(uiStateSubject)
    }


    fun processClicks(observable: Observable<StopWatch>) =
        observable.subscribe(clicksHandlerSubject::onNext)

    fun uiState(): Observable<String> = uiStateSubject.hide()

    fun buttonState() = buttonStateSubject.hide()

    override fun onCleared() {
        clicksHandlerSubject.onComplete()
        super.onCleared()
    }

    private fun timerObservable() =
        Observable.interval(0L, 1L, TimeUnit.SECONDS)
            .map { it + resumeSubject.value!! }
            .takeWhile { it <= 3600L }

}

class MainActivity : AppCompatActivity() {

    private val disposable = CompositeDisposable()

    lateinit var viewModel: StopWatchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(StopWatchViewModel::class.java)
        setContentView(R.layout.activity_main)

        clickObservables()
            .let(viewModel::processClicks)
            .let(disposable::add)

        viewModel
            .uiState()
            .subscribe(display::setText)
            .let(disposable::add)

        viewModel.buttonState()
            .subscribe(::buttonsState)
            .let(disposable::add)
    }

    override fun onDestroy() {
        if (!disposable.isDisposed)
            disposable.dispose()
        super.onDestroy()
    }


    private fun clickObservables(): Observable<StopWatch> = Observable.merge(
        listOf(
            start.clicks().map { StopWatch.Start },
            reset.clicks().map { StopWatch.Reset },
            pause.clicks().map { StopWatch.Pause }

        )
    )

    private fun buttonsState(value: Triple<Boolean, Boolean, Boolean>) {
        start.isEnabled = value.first
        reset.isEnabled = value.second
        pause.isEnabled = value.third
    }
}

enum class StopWatch {
    Start,
    Reset,
    Pause
}

