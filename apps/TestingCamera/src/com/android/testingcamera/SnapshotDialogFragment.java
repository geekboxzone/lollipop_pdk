package com.android.testingcamera;
import com.android.testingcamera.R;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class SnapshotDialogFragment extends DialogFragment implements View.OnClickListener{

    private ImageView mInfoImage;
    private TextView mInfoText;
    private Button mOkButton;

    private Bitmap mImage;
    private String mImageString;


    public SnapshotDialogFragment() {
        // Empty constructor required for DialogFragment
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_snapshot, container);

        mOkButton = (Button) view.findViewById(R.id.snapshot_ok);
        mOkButton.setOnClickListener(this);

        mInfoImage = (ImageView) view.findViewById(R.id.snapshot_image);
        mInfoImage.setImageBitmap(mImage);
        mInfoText= (TextView) view.findViewById(R.id.snapshot_text);
        mInfoText.setText(mImageString);

        getDialog().setTitle("Snapshot result");
        return view;
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        this.dismiss();
    }

    public void updateImage(Bitmap image) {
        mImage = image;
    }
}
