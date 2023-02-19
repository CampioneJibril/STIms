package com.example.stims_v9;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.stims_v9.Button.Capture;
import com.example.stims_v9.Button.SubjectFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ScanFragment extends Fragment {

    //Initialize variable
    String date = new SimpleDateFormat("yyyy, MMMM, d,EEEE", Locale.getDefault()).format(new Date());
    String time = new SimpleDateFormat("h:mm:a", Locale.getDefault()).format(new Date());
    String nameModel, selectedSubject, userId, userName;
    Button btn_scan ;
    MaterialButton btn_subjects;
    Spinner spinner_subjects;
    Switch switchCheckInAndOut;
    TextView text_view_subject_selected;

    ArrayList<String> everyStudent = new ArrayList<>();
    ArrayList <String> subjectList = new ArrayList<>();

    FirebaseAuth mAuth;
    FirebaseUser currentUser;
    boolean switchState;

    //Initialize FirebaseDatabase
    private final FirebaseDatabase studentDatabase = FirebaseDatabase.getInstance("https://stims-v9-default-rtdb.asia-southeast1.firebasedatabase.app/");
    private final DatabaseReference root = studentDatabase.getReference();

    DatabaseReference everySubjectRef = root.child("Subjects");


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_scan, container, false);


        text_view_subject_selected = v.findViewById(R.id.text_view_subject_selected);

        spinner_subjects = v.findViewById(R.id.spinner_subjects);

        btn_subjects = v.findViewById(R.id.btn_subjects);
        btn_scan = v.findViewById(R.id.btn_scan);

        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        switchCheckInAndOut = v.findViewById(R.id.switchCheckInAndOut);
        switchCheckInAndOut.setVisibility(View.GONE);
        switchCheckInAndOut.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                switchState = b;
                changeSwitchText();
            }
        });

        everySubjectRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                updateSubjectSpinner(dataSnapshot);
                if(isAdded()) {
                    ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, subjectList);
                    spinner_subjects.setAdapter(subjectAdapter);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {   }});

        spinner_subjects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedSubject = spinner_subjects.getSelectedItem().toString();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }});



        final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
                result -> {
                    result.getClass();
                    String scanResult = result.getContents();
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle("Student Name");
                    builder.setMessage("Selected Subject: "+ selectedSubject );
                    builder.setPositiveButton("CANCEL", (dialogInterface, i) -> {
                        Toast.makeText(getActivity(), "Cancelled", Toast.LENGTH_SHORT).show();
                        dialogInterface.dismiss();
                    });
                    builder.setNegativeButton("CHECK IN/OUT", (dialogInterface, i) -> {


                        DatabaseReference suggestionRef = root.child("Suggestions");

                        DatabaseReference everyStudentRef = root.child("Students").child(selectedSubject);

                        DatabaseReference userDataRef = root.child("UserData").child(scanResult);
                        userDataRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                userName = dataSnapshot.child("name").getValue(String.class);
                            }
                            @Override
                            public void onCancelled (@NonNull DatabaseError error){   }});

                        everyStudentRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                everyStudent.clear();
                                for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) {
                                    nameModel = childSnapshot.getValue().toString();
                                    everyStudent.add(nameModel);
                                }
                                if(!everyStudent.contains(userName)){
                                    String name = userName;
                                    everyStudentRef.push().child("student_name").setValue(name);
                                    suggestionRef.push().setValue(scanResult);
                                }

                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {  }
                        });


                        DatabaseReference attendanceRootRef = root.child("Attendance").child(selectedSubject).child(date).child(scanResult);
                        attendanceRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (snapshot.child("Check_In").exists()) {
                                        checkOutData(attendanceRootRef, time);
                                    } else {
                                        checkInData(attendanceRootRef,date, time,scanResult, userName);
                                    }
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                }
                            });


                });
        builder.show();
    });

        btn_scan.setOnClickListener(view -> {
            barcodeLauncher.launch(new ScanOptions());
            startScan();
        });

        btn_subjects.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replaceFragment(new SubjectFragment());
                hideButtons(btn_scan, btn_subjects, spinner_subjects, text_view_subject_selected);
            }
        });

        return v;
    }


    public void switchBtnIfAndElse(DatabaseReference databaseReference, String date, String check_in, String uid, String name){
        if(switchState){
            checkOutData(databaseReference, time);
        }else{
            checkInData(databaseReference, date, check_in, uid, name);
        }
    }
    public void changeSwitchText(){
        if (switchState) {
            switchCheckInAndOut.setText("CHECK OUT");
        } else {
            switchCheckInAndOut.setText("CHECK IN");
        }
    }
    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getChildFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.frame_layout, fragment);
        fragmentTransaction.commit();
    }

    public void hideButtons(View btnScan, View btnSubjects, View spinnerSubjects, View text_view_subject_selected) {
        btnScan.setVisibility(View.GONE);
        btnSubjects.setVisibility(View.GONE);
        spinnerSubjects.setVisibility(View.GONE);
        text_view_subject_selected.setVisibility(View.GONE);
    }

    public void updateSubjectSpinner(DataSnapshot dataSnapshot) {
        subjectList.clear();
        for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) {
            String value = childSnapshot.getValue(String.class);
            subjectList.add(value);
        }
    }
    public void checkInData(DatabaseReference databaseReferenceRef, String date, String check_in, String uid, String name){
        databaseReferenceRef.child("Date").setValue(date);
        databaseReferenceRef.child("Check_In").setValue(check_in);
        databaseReferenceRef.child("UID").setValue(uid);
        databaseReferenceRef.child("Name").setValue(name);
    }

    public void checkOutData(DatabaseReference databaseReferenceRef, String time){
        databaseReferenceRef.child("Check_Out").setValue(time).addOnSuccessListener(unused -> Toast.makeText(getActivity(), "Check Out Successfully", Toast.LENGTH_SHORT).show());
    }

    public void startScan(){
        Toast.makeText(getActivity(), "For Flash use Volume up Key", Toast.LENGTH_SHORT).show();
        ScanOptions options = new ScanOptions();
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(Capture.class);
    }
}


