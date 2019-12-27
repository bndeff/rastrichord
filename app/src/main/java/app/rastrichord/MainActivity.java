package app.rastrichord;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.view.ViewCompat;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Space;

import org.billthefarmer.mididriver.MidiDriver;

import java.util.ArrayList;
import java.util.List;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

public class MainActivity extends AppCompatActivity
        implements View.OnClickListener, View.OnTouchListener {

    private enum NoteType {
        LINE,
        LEDGER,
        SPACE,
        ACCIDENTAL,
        INVALID
    }

    private static final String FLAT = "♭";
    private static final String NATURAL = "♮";
    private static final String SHARP = "♯";

    private static final String[] SUP = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶"};

    private static final int COLOR_LINE = 0xff808080;
    private static final int COLOR_HEADER = 0xffd0d0d0;
    private static final int COLOR_BUTTON = 0xffd0d0d0;
    private static final int COLOR_SIG = 0xfff0f080;
    private static final int COLOR_ACC = 0xff80e080;

    private List<LinearLayout> rows;
    private List<Integer> notes;
    private List<Integer> sig;
    private List<Integer> acc;
    private List<Integer> midiNotes;
    private List<Boolean> playing;
    private List<List<AppCompatButton>> buttons;
    private int keySig;
    private int numNotes;
    private static final int minNote = 38;
    private static final int maxNote = 79;

    private MidiDriver midiDriver;

    private static NoteType noteType(int midiNote) {
        if (midiNote < 0 || midiNote > 127) return NoteType.INVALID;
        final int octave = midiNote / 12;
        final int note = midiNote % 12;
        final int offset;
        switch(note) {
            case 0:  offset = 0; break;  // C
            case 2:  offset = 1; break;  // D
            case 4:  offset = 2; break;  // E
            case 5:  offset = 3; break;  // F
            case 7:  offset = 4; break;  // G
            case 9:  offset = 5; break;  // A
            case 11: offset = 6; break;  // B
            default: return NoteType.ACCIDENTAL;
        }
        if((octave+offset)%2==0) return NoteType.SPACE;
        if(midiNote >= 41 && midiNote <= 57) return NoteType.LINE;  // treble clef
        if(midiNote >= 64 && midiNote <= 77) return NoteType.LINE;  // bass clef
        return NoteType.LEDGER;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LinearLayout keyPanel = findViewById(R.id.keyPanel);
        LinearLayout sigRow = new LinearLayout(this);
        sigRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0, 2.0f));
        keyPanel.addView(sigRow);
        for(int j=0; j<7; ++j) {
            if(j % 2 == 0) {
                Space space;
                space = new Space(this);
                space.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
                sigRow.addView(space);
            } else {
                AppCompatButton button;
                button = new AppCompatButton(this);
                button.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
                button.setOnClickListener(this);
                switch(j) {
                    case 1: button.setText(FLAT); break;
                    case 3: button.setText(NATURAL); break;
                    case 5: button.setText(SHARP); break;
                }
                button.setAutoSizeTextTypeUniformWithConfiguration(8, 48, 1, COMPLEX_UNIT_DIP);
                button.setGravity(Gravity.CENTER);
                sigRow.addView(button);
            }
        }
        keySig = 0;
        numNotes = 0;
        rows = new ArrayList<>();
        notes = new ArrayList<>();
        sig = new ArrayList<>();
        acc = new ArrayList<>();
        playing = new ArrayList<>();
        buttons = new ArrayList<>();
        for(int i=0; i<128; ++i) {
            playing.add(false);
            buttons.add(new ArrayList<AppCompatButton>());
        }
        for(int i=maxNote; i>=minNote; --i){
            NoteType nt = noteType(i);
            if(nt == NoteType.LINE || nt == NoteType.LEDGER || nt == NoteType.SPACE) {
                RelativeLayout rowRoot = new RelativeLayout(this);
                rowRoot.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
                keyPanel.addView(rowRoot);
                if(nt == NoteType.LINE || nt == NoteType.LEDGER) {
                    LinearLayout lineRoot = new LinearLayout(this);
                    lineRoot.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    lineRoot.setOrientation(LinearLayout.VERTICAL);
                    Space space;
                    View rect;
                    space = new Space(this);
                    space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3.0f));
                    lineRoot.addView(space);
                    if(nt == NoteType.LINE) {
                        rect = new View(this);
                        rect.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
                        rect.setBackgroundColor(COLOR_LINE);
                        lineRoot.addView(rect);
                    } else {
                        LinearLayout ledgerRoot = new LinearLayout(this);
                        ledgerRoot.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
                        lineRoot.addView(ledgerRoot);
                        for(int j=0; j<7; ++j) {
                            if(j % 2 == 0) {
                                boolean edge = j == 0 || j == 6;
                                space = new Space(this);
                                space.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, edge ? 5.0f : 4.0f));
                                ledgerRoot.addView(space);
                            } else {
                                rect = new View(this);
                                rect.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 8.0f));
                                rect.setBackgroundColor(COLOR_LINE);
                                if(j != 3) {
                                    NoteType nti = noteType(i + (j-3)/2);
                                    if(nti != NoteType.ACCIDENTAL) {
                                        rect.setVisibility(View.INVISIBLE);
                                    }
                                }
                                ledgerRoot.addView(rect);
                            }
                        }
                    }
                    space = new Space(this);
                    space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3.0f));
                    lineRoot.addView(space);
                    rowRoot.addView(lineRoot);
                }
                LinearLayout row = new LinearLayout(this);
                row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                ++numNotes;
                rows.add(row);
                notes.add(i);
                sig.add(0);
                acc.add(0);
                rowRoot.addView(row);
                for(int j=0; j<7; ++j) {
                    if(j % 2 == 0) {
                        Space space;
                        space = new Space(this);
                        space.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
                        row.addView(space);
                    } else {
                        AppCompatButton button;
                        button = new AppCompatButton(this);
                        button.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
                        button.setOnTouchListener(this);
                        if(j != 3) {
                            NoteType nti = noteType(i + (j-3)/2);
                            if (nti != NoteType.ACCIDENTAL) {
                                button.setVisibility(View.INVISIBLE);
                            }
                        }
                        row.addView(button);
                        buttons.get(i + (j-3)/2).add(button);
                    }
                }
            }
        }
        midiNotes = new ArrayList<>();
        for(int i=0; i<128; ++i) {
            if(!buttons.get(i).isEmpty()) {
                midiNotes.add(i);
            }
        }
        keyPanel.setOnClickListener(this);
        setButtonColor(sigRow, -1, COLOR_HEADER);
        setButtonColor(sigRow, 0, COLOR_HEADER);
        setButtonColor(sigRow, 1, COLOR_HEADER);
        refreshColors();
        midiDriver = new MidiDriver();
    }

    private void updateSig() {
        for(int i=0; i<numNotes; ++i) {
            int midiNote = notes.get(i);
            int note = midiNote % 12;
            int curSig = 0;
            switch(note) {
                // circle of fifths
                case 0:  // C
                    if(keySig >= +2) curSig = +1;
                    break;
                case 2:  // D
                    if(keySig <= -4) curSig = -1;
                    if(keySig >= +4) curSig = +1;
                    break;
                case 4:  // E
                    if(keySig <= -2) curSig = -1;
                    break;
                case 5:  // F
                    if(keySig >= +1) curSig = +1;
                    break;
                case 7:  // G
                    if(keySig <= -5) curSig = -1;
                    if(keySig >= +3) curSig = +1;
                    break;
                case 9:  // A
                    if(keySig <= -3) curSig = -1;
                    if(keySig >= +5) curSig = +1;
                    break;
                case 11: // B
                    if(keySig <= -1) curSig = -1;
                    break;
            }
            sig.set(i, curSig);
        }
    }

    private void resetAcc() {
        for(int i=0; i<numNotes; ++i) {
            acc.set(i, sig.get(i));
        }
    }

    private void setButtonColor(LinearLayout row, int index, int color) {
        AppCompatButton button = (AppCompatButton) row.getChildAt(3 + 2*index);
        ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(color));
    }

    private void refreshColors() {
        for(int i=0; i<numNotes; ++i) {
            int curSig = sig.get(i);
            int curAcc = acc.get(i);
            LinearLayout row = rows.get(i);
            setButtonColor(row, -1, COLOR_BUTTON);
            setButtonColor(row, 0, COLOR_BUTTON);
            setButtonColor(row, 1, COLOR_BUTTON);
            setButtonColor(row, curSig, COLOR_SIG);
            setButtonColor(row, curAcc, COLOR_ACC);
        }
    }

    @SuppressLint("SetTextI18n")
    private void refreshHeaders() {
        LinearLayout keyPanel = findViewById(R.id.keyPanel);
        LinearLayout sigRow = (LinearLayout) keyPanel.getChildAt(0);
        AppCompatButton bFlat = (AppCompatButton) sigRow.getChildAt(1);
        AppCompatButton bNatural = (AppCompatButton) sigRow.getChildAt(3);
        AppCompatButton bSharp = (AppCompatButton) sigRow.getChildAt(5);
        bFlat.setText(FLAT);
        bNatural.setText(NATURAL);
        bSharp.setText(SHARP);
        if(keySig < 0) {
            bFlat.setText(FLAT + SUP[-keySig]);
        } else if(keySig > 0) {
            bSharp.setText(SHARP + SUP[keySig]);
        }
    }

    @Override
    public void onClick(View v) {
        LinearLayout keyPanel = findViewById(R.id.keyPanel);
        if(v != keyPanel) {
            LinearLayout row = (LinearLayout) v.getParent();
            int opSig = (row.indexOfChild(v) - 1) / 2 - 1;
            if (opSig == 0) {
                keySig = 0;
            } else {
                keySig += opSig;
                if (keySig < -5) keySig = -5;
                if (keySig > +5) keySig = +5;
            }
            updateSig();
        }
        resetAcc();
        refreshColors();
        refreshHeaders();
    }

    private void midiEvent(int midiNote, boolean state) {
        byte[] event = new byte[3];
        event[0] = (byte) (state ? 0x90 : 0x80);  // 0x90: note on, 0x80: note off + 0x00: channel 1
        event[1] = (byte) midiNote;
        event[2] = (byte) (state ? 0x7f : 0x00);  // velocity
        midiDriver.write(event);
    }

    private void refreshPlaying() {
        for(int i : midiNotes) {
            boolean state = false;
            for(AppCompatButton button : buttons.get(i)) {
                if (button.isPressed()) state = true;
            }
            if(playing.get(i) != state) {
                playing.set(i, state);
                midiEvent(i, state);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            v.setPressed(true);
            LinearLayout row = (LinearLayout) v.getParent();
            int rowIndex = rows.indexOf(row);
            int curAcc = (row.indexOfChild(v)-1)/2-1;
            acc.set(rowIndex, curAcc);
            refreshColors();
        } else if(event.getAction() == MotionEvent.ACTION_UP) {
            v.setPressed(false);
        }
        refreshPlaying();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        midiDriver.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        midiDriver.stop();
    }
}
