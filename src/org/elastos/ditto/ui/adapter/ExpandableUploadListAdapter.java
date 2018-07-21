/**
 *  ownCloud Android client application
 *
 *  @author LukeOwncloud
 *  @author masensio
 *  Copyright (C) 2016 ownCloud GmbH.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2,
 *  as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.elastos.ditto.ui.adapter;

import android.accounts.Account;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.support.design.widget.Snackbar;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.elastos.ditto.R;
import org.elastos.ditto.authentication.AccountUtils;
import org.elastos.ditto.datamodel.OCFile;
import org.elastos.ditto.datamodel.ThumbnailsCacheManager;
import org.elastos.ditto.datamodel.UploadsStorageManager;
import org.elastos.ditto.datamodel.UploadsStorageManager.UploadStatus;
import org.elastos.ditto.db.OCUpload;
import org.elastos.ditto.db.UploadResult;
import org.elastos.ditto.files.services.FileUploader;
import org.elastos.ditto.files.services.TransferRequester;
import org.elastos.ditto.lib.common.OwnCloudAccount;
import org.elastos.ditto.lib.common.network.OnDatatransferProgressListener;
import org.elastos.ditto.lib.common.utils.Log_OC;
import org.elastos.ditto.ui.activity.FileActivity;
import org.elastos.ditto.utils.DisplayUtils;
import org.elastos.ditto.utils.MimetypeIconUtil;

import org.elastos.ditto.db.OCUpload;
import org.elastos.ditto.db.UploadResult;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Observable;
import java.util.Observer;

/**
 * This Adapter populates a ListView with following types of uploads: pending,
 * active, completed. Filtering possible.
 *
 */
public class ExpandableUploadListAdapter extends BaseExpandableListAdapter implements Observer {

    private static final String TAG = ExpandableUploadListAdapter.class.getSimpleName();
    private FileActivity mParentActivity;

    private UploadsStorageManager mUploadsStorageManager;

    public ProgressListener mProgressListener;

    interface Refresh {
        public void refresh();
    }

    abstract class UploadGroup implements Refresh {
        OCUpload[] items;
        String name;

        public UploadGroup(String groupName) {
            this.name = groupName;
            items = new OCUpload[0];
        }

        public String getGroupName() {
            return name;
        }

        public int getGroupCount() {
            return items == null ? 0 : items.length;
        }

        public Comparator<OCUpload> comparator = new Comparator<OCUpload>() {

            @Override
            public int compare(OCUpload upload1, OCUpload upload2) {
                if (upload1.getUploadStatus().equals(UploadStatus.UPLOAD_IN_PROGRESS)) {
                    if (!upload2.getUploadStatus().equals(UploadStatus.UPLOAD_IN_PROGRESS)) {
                        return -1;
                    }
                    // both are in progress
                    FileUploader.FileUploaderBinder binder = mParentActivity.getFileUploaderBinder();
                    if (binder != null) {
                        if (binder.isUploadingNow(upload1)) {
                            return -1;
                        } else if (binder.isUploadingNow(upload2)) {
                            return 1;
                        }
                    }
                } else if (upload2.getUploadStatus().equals(UploadStatus.UPLOAD_IN_PROGRESS)) {
                    return 1;
                }
                if (upload1.getUploadEndTimestamp() == 0 || upload2.getUploadEndTimestamp() == 0) {
                    return compareUploadId(upload1, upload2);
                } else {
                    return compareUpdateTime(upload1, upload2);
                }
            }

            private int compareUploadId(OCUpload upload1, OCUpload upload2) {
                return Long.valueOf(upload1.getUploadId()).compareTo(upload2.getUploadId());
            }

            private int compareUpdateTime(OCUpload upload1, OCUpload upload2) {
                return Long.valueOf(upload2.getUploadEndTimestamp()).compareTo(upload1.getUploadEndTimestamp());
            }
        };

        abstract public int getGroupIcon();
    }

    private UploadGroup[] mUploadGroups = null;

    public ExpandableUploadListAdapter(FileActivity parentActivity) {
        Log_OC.d(TAG, "ExpandableUploadListAdapter");
        mParentActivity = parentActivity;
        mUploadsStorageManager = new UploadsStorageManager(mParentActivity.getContentResolver());
        mUploadGroups = new UploadGroup[3];
        mUploadGroups[0] = new UploadGroup(mParentActivity.getString(R.string.uploads_view_group_current_uploads)) {
            @Override
            public void refresh() {
                items = mUploadsStorageManager.getCurrentAndPendingUploads();
                Arrays.sort(items, comparator);
            }

            @Override
            public int getGroupIcon() {
                return R.drawable.upload_in_progress;
            }
        };
        mUploadGroups[1] = new UploadGroup(mParentActivity.getString(R.string.uploads_view_group_failed_uploads)) {
            @Override
            public void refresh() {
                items = mUploadsStorageManager.getFailedButNotDelayedForWifiUploads();
                Arrays.sort(items, comparator);
            }

            @Override
            public int getGroupIcon() {
                return R.drawable.upload_failed;
            }

        };
        mUploadGroups[2] = new UploadGroup(mParentActivity.getString(R.string.uploads_view_group_finished_uploads)) {
            @Override
            public void refresh() {
                items = mUploadsStorageManager.getFinishedUploads();
                Arrays.sort(items, comparator);
            }

            @Override
            public int getGroupIcon() {
                return R.drawable.upload_finished;
            }

        };
        loadUploadItemsFromDb();
    }


    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        super.registerDataSetObserver(observer);
        mUploadsStorageManager.addObserver(this);
        Log_OC.d(TAG, "registerDataSetObserver");
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        super.unregisterDataSetObserver(observer);
        mUploadsStorageManager.deleteObserver(this);
        Log_OC.d(TAG, "unregisterDataSetObserver");
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    private View getView(OCUpload[] uploadsItems, int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflator =
                    (LayoutInflater) mParentActivity.getSystemService(
                            Context.LAYOUT_INFLATER_SERVICE
                    );
            view = inflator.inflate(R.layout.upload_list_item, parent, false);
        }

        if (uploadsItems != null && uploadsItems.length > position) {
            final OCUpload upload = uploadsItems[position];

            // local file name
            TextView fileTextView = (TextView) view.findViewById(R.id.upload_name);
            File remoteFile = new File(upload.getRemotePath());
            String fileName = remoteFile.getName();
            if (fileName.length() == 0) {
                fileName = File.separator;
            }
            fileTextView.setText(fileName);

            // remote path to parent folder
            TextView pathTextView = (TextView) view.findViewById(R.id.upload_remote_path);
            String remoteParentPath = upload.getRemotePath();
            remoteParentPath = new File(remoteParentPath).getParent();
            pathTextView.setText(mParentActivity.getString(R.string.app_name) + remoteParentPath);

            // file size
            TextView fileSizeTextView = (TextView) view.findViewById(R.id.upload_file_size);
            fileSizeTextView.setText(
                DisplayUtils.bytesToHumanReadable(
                    upload.getFileSize(),
                    mParentActivity
                ) + ", "
            );

            //* upload date
            TextView uploadDateTextView = (TextView) view.findViewById(R.id.upload_date);
            long updateTime = upload.getUploadEndTimestamp();
            CharSequence dateString = DisplayUtils.getRelativeDateTimeString(
                    mParentActivity,
                    updateTime,
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    0
            );
            uploadDateTextView.setText(dateString);

            TextView accountNameTextView = (TextView) view.findViewById(R.id.upload_account);
            try {
                Account account = AccountUtils.getOwnCloudAccountByName(mParentActivity, upload.getAccountName());
                OwnCloudAccount oca = new OwnCloudAccount(account, mParentActivity);
                accountNameTextView.setText(
                    oca.getDisplayName() + " @ " +
                    DisplayUtils.convertIdn(account.name.substring(account.name.lastIndexOf("@") + 1), false)
                );
            } catch (Exception e) {
                Log_OC.w(TAG, "Couldn't get display name for account, using old style");
                accountNameTextView.setText(upload.getAccountName());
            }

            TextView statusTextView = (TextView) view.findViewById(R.id.upload_status);

            ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.upload_progress_bar);

            /// Reset fields visibility
            uploadDateTextView.setVisibility(View.VISIBLE);
            pathTextView.setVisibility(View.VISIBLE);
            fileSizeTextView.setVisibility(View.VISIBLE);
            accountNameTextView.setVisibility(View.VISIBLE);
            statusTextView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);

            /// Update information depending of upload details
            String status = getStatusText(upload);
            switch (upload.getUploadStatus()) {
                case UPLOAD_IN_PROGRESS:
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.VISIBLE);

                    FileUploader.FileUploaderBinder binder = mParentActivity.getFileUploaderBinder();
                    if (binder != null) {
                        if (binder.isUploadingNow(upload)) {
                            /// really uploading, so...
                            /// ... unbind the old progress bar, if any; ...
                            if (mProgressListener != null) {
                                binder.removeDatatransferProgressListener(
                                    mProgressListener,
                                    mProgressListener.getUpload()   // the one that was added
                                );
                            }
                            /// ... then, bind the current progress bar to listen for updates
                            mProgressListener = new ProgressListener(upload, progressBar);
                            binder.addDatatransferProgressListener(
                                mProgressListener,
                                upload
                            );

                        } else {
                            /// not really uploading; stop listening progress if view is reused!
                            if (convertView != null &&
                                    mProgressListener != null &&
                                    mProgressListener.isWrapping(progressBar))  {
                                binder.removeDatatransferProgressListener(
                                    mProgressListener,
                                    mProgressListener.getUpload()   // the one that was added
                                );
                                mProgressListener = null;
                            }
                        }
                    } else {
                        Log_OC.w(
                            TAG,
                            "FileUploaderBinder not ready yet for upload " + upload.getRemotePath()
                        );
                    }
                    uploadDateTextView.setVisibility(View.GONE);
                    pathTextView.setVisibility(View.GONE);
                    fileSizeTextView.setVisibility(View.GONE);
                    progressBar.invalidate();
                    break;

                case UPLOAD_FAILED:
                    uploadDateTextView.setVisibility(View.GONE);
                    break;

                case UPLOAD_SUCCEEDED:
                    statusTextView.setVisibility(View.GONE);
                    break;
            }
            statusTextView.setText(status);

            /// bind listeners to perform actions
            ImageButton rightButton = (ImageButton) view.findViewById(R.id.upload_right_button);
            if (upload.getUploadStatus() == UploadStatus.UPLOAD_IN_PROGRESS) {
                //Cancel
                rightButton.setImageResource(R.drawable.ic_action_cancel_grey);
                rightButton.setVisibility(View.VISIBLE);
                rightButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FileUploader.FileUploaderBinder uploaderBinder = mParentActivity.getFileUploaderBinder();
                        if (uploaderBinder != null) {
                            uploaderBinder.cancel(upload);
                            refreshView();
                        }
                    }
                });

            } else if (upload.getUploadStatus() == UploadStatus.UPLOAD_FAILED) {
                //Delete
                rightButton.setImageResource(R.drawable.ic_action_delete_grey);
                rightButton.setVisibility(View.VISIBLE);
                rightButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mUploadsStorageManager.removeUpload(upload);
                        refreshView();
                    }
                });

            } else {    // UploadStatus.UPLOAD_SUCCESS
                rightButton.setVisibility(View.INVISIBLE);
            }

            // retry
            if (upload.getUploadStatus() == UploadStatus.UPLOAD_FAILED) {
                if (UploadResult.CREDENTIAL_ERROR.equals(upload.getLastResult())) {
                    view.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mParentActivity.getFileOperationsHelper().checkCurrentCredentials(
                                upload.getAccount(mParentActivity)
                            );
                        }
                    });

                } else {
                    // not a credentials error
                    view.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            File file = new File(upload.getLocalPath());
                            if (file.exists()) {
                                TransferRequester requester = new TransferRequester();
                                requester.retry(mParentActivity, upload);
                                refreshView();
                            } else {
                                final String message = String.format(
                                    mParentActivity.getString(R.string.local_file_not_found_toast)
                                );
                                Snackbar snackbar = Snackbar.make(
                                    v.getRootView().findViewById(android.R.id.content),
                                    message,
                                    Snackbar.LENGTH_LONG
                                );
                                snackbar.show();
                            }
                        }
                    });
                }
            } else {
                view.setOnClickListener(null);
            }

            /// Set icon or thumbnail
            ImageView fileIcon = (ImageView) view.findViewById(R.id.thumbnail);
            fileIcon.setImageResource(R.drawable.file);

            /** Cancellation needs do be checked and done before changing the drawable in fileIcon, or
             * {@link ThumbnailsCacheManager#cancelPotentialWork} will NEVER cancel any task.
             **/
            OCFile fakeFileToCheatThumbnailsCacheManagerInterface = new OCFile(upload.getRemotePath());
            fakeFileToCheatThumbnailsCacheManagerInterface.setStoragePath(upload.getLocalPath());
            fakeFileToCheatThumbnailsCacheManagerInterface.setMimetype(upload.getMimeType());

            boolean allowedToCreateNewThumbnail = (ThumbnailsCacheManager.cancelPotentialThumbnailWork(
                    fakeFileToCheatThumbnailsCacheManagerInterface,
                    fileIcon)
            );

            // TODO this code is duplicated; refactor to a common place
            if ((fakeFileToCheatThumbnailsCacheManagerInterface.isImage()
                    && fakeFileToCheatThumbnailsCacheManagerInterface.getRemoteId() != null &&
                    upload.getUploadStatus() == UploadStatus.UPLOAD_SUCCEEDED)) {
                // Thumbnail in Cache?
                Bitmap thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(
                        String.valueOf(fakeFileToCheatThumbnailsCacheManagerInterface.getRemoteId())
                );
                if (thumbnail != null && !fakeFileToCheatThumbnailsCacheManagerInterface.needsUpdateThumbnail()) {
                    fileIcon.setImageBitmap(thumbnail);
                } else {
                    // generate new Thumbnail
                    if (allowedToCreateNewThumbnail) {
                        final ThumbnailsCacheManager.ThumbnailGenerationTask task =
                                new ThumbnailsCacheManager.ThumbnailGenerationTask(
                                        fileIcon, mParentActivity.getStorageManager(), mParentActivity.getAccount()
                                );
                        if (thumbnail == null) {
                            thumbnail = ThumbnailsCacheManager.mDefaultImg;
                        }
                        final ThumbnailsCacheManager.AsyncThumbnailDrawable asyncDrawable =
                                new ThumbnailsCacheManager.AsyncThumbnailDrawable(
                                        mParentActivity.getResources(),
                                        thumbnail,
                                        task
                                );
                        fileIcon.setImageDrawable(asyncDrawable);
                        task.execute(fakeFileToCheatThumbnailsCacheManagerInterface);
                    }
                }

                if ("image/png".equals(upload.getMimeType())) {
                    fileIcon.setBackgroundColor(mParentActivity.getResources()
                            .getColor(R.color.background_color));
                }


            } else if (fakeFileToCheatThumbnailsCacheManagerInterface.isImage()) {
                File file = new File(upload.getLocalPath());
                // Thumbnail in Cache?
                Bitmap thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(
                        String.valueOf(file.hashCode()));
                if (thumbnail != null) {
                    fileIcon.setImageBitmap(thumbnail);
                } else {
                    // generate new Thumbnail
                    if (allowedToCreateNewThumbnail) {
                        final ThumbnailsCacheManager.ThumbnailGenerationTask task =
                                new ThumbnailsCacheManager.ThumbnailGenerationTask(fileIcon);
                        if (thumbnail == null) {
                            thumbnail = ThumbnailsCacheManager.mDefaultImg;
                        }
                        final ThumbnailsCacheManager.AsyncThumbnailDrawable asyncDrawable =
                                new ThumbnailsCacheManager.AsyncThumbnailDrawable(
                                        mParentActivity.getResources(),
                                        thumbnail,
                                        task
                                );
                        fileIcon.setImageDrawable(asyncDrawable);
                        task.execute(file);
                        Log_OC.v(TAG, "Executing task to generate a new thumbnail");
                    }
                }

                if ("image/png".equalsIgnoreCase(upload.getMimeType())) {
                    fileIcon.setBackgroundColor(mParentActivity.getResources()
                            .getColor(R.color.background_color));
                }
            } else {
                fileIcon.setImageResource(MimetypeIconUtil.getFileTypeIconId(
                        upload.getMimeType(),
                        fileName
                ));
            }

        }

        return view;
    }

    /**
     * Gets the status text to show to the user according to the status and last result of the
     * the given upload.
     *
     * @param upload        Upload to describe.
     * @return              Text describing the status of the given upload.
     */
    private String getStatusText(OCUpload upload) {

        String status;
        switch (upload.getUploadStatus()) {

            case UPLOAD_IN_PROGRESS:
                status = mParentActivity.getString(R.string.uploads_view_later_waiting_to_upload);
                FileUploader.FileUploaderBinder binder = mParentActivity.getFileUploaderBinder();
                if (binder != null && binder.isUploadingNow(upload)) {
                    /// really uploading, bind the progress bar to listen for progress updates
                    status = mParentActivity.getString(R.string.uploader_upload_in_progress_ticker);
                }
                break;

            case UPLOAD_SUCCEEDED:
                status = mParentActivity.getString(R.string.uploads_view_upload_status_succeeded);
                break;

            case UPLOAD_FAILED:
                switch (upload.getLastResult()) {
                    case CREDENTIAL_ERROR:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_failed_credentials_error
                        );
                        break;
                    case FOLDER_ERROR:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_failed_folder_error
                        );
                        break;
                    case FILE_NOT_FOUND:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_failed_localfile_error
                        );
                        break;
                    case FILE_ERROR:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_failed_file_error
                        );
                        break;
                    case PRIVILEDGES_ERROR:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_failed_permission_error
                        );
                        break;
                    case NETWORK_CONNECTION:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_failed_connection_error
                        );
                        break;
                    case DELAYED_FOR_WIFI:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_waiting_for_wifi
                        );
                        break;
                    case CONFLICT_ERROR:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_conflict
                        );
                        break;
                    case SERVICE_INTERRUPTED:
                        status =  mParentActivity.getString(
                            R.string.uploads_view_upload_status_service_interrupted
                        );
                        break;
                    case SERVICE_UNAVAILABLE:
                        status = mParentActivity.getString(R.string.service_unavailable);
                        break;
                    case QUOTA_EXCEEDED:
                        status = mParentActivity.getString(R.string.failed_upload_quota_exceeded_text);
                        break;
                    case SSL_RECOVERABLE_PEER_UNVERIFIED:
                        status =
                            mParentActivity.getString(
                                R.string.ssl_certificate_not_trusted
                            );
                        break;
                    case UNKNOWN:
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_unknown_fail
                        );
                        break;
                    case CANCELLED:
                        // should not get here ; cancelled uploads should be wiped out
                        status = mParentActivity.getString(
                            R.string.uploads_view_upload_status_cancelled
                        );
                        break;
                    case UPLOADED:
                        // should not get here ; status should be UPLOAD_SUCCESS
                        status =  mParentActivity.getString(R.string.uploads_view_upload_status_succeeded);
                        break;
                    case SPECIFIC_FORBIDDEN:
                        // We don't know the specific forbidden error message because it is not being
                        // saved in uploads storage
                        status = String.format(mParentActivity.getString(R.string.uploader_upload_forbidden));
                        break;
                    case SPECIFIC_SERVICE_UNAVAILABLE:
                        // We don't know the specific unavailable service error message because
                        // it is not being saved in uploads storage
                        status = mParentActivity.getString(R.string.service_unavailable);
                        break;
                    case SPECIFIC_UNSUPPORTED_MEDIA_TYPE:
                        // We don't know the specific unsupported media type error message because
                        // it is not being saved in uploads storage
                        status = mParentActivity.getString(R.string.uploads_view_unsupported_media_type);
                        break;
                    default:
                        status = "Naughty devs added a new fail result but no description for the user";
                        break;
                }
                break;

            default:
                status = "Uncontrolled status: " + upload.getUploadStatus().toString();
        }
        return status;
    }


    @Override
    public boolean hasStableIds() {
        return false;
    }


    /**
     * Load upload items from {@link UploadsStorageManager}.
     */
    private void loadUploadItemsFromDb() {
        Log_OC.d(TAG, "loadUploadItemsFromDb");

        for (UploadGroup group : mUploadGroups) {
            group.refresh();
        }

        notifyDataSetChanged();
    }

    @Override
    public void update(Observable arg0, Object arg1) {
        Log_OC.d(TAG, "update");
        loadUploadItemsFromDb();
    }


    public void refreshView() {
        Log_OC.d(TAG, "refreshView");
        loadUploadItemsFromDb();
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return mUploadGroups[(int) getGroupId(groupPosition)].items[childPosition];
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView,
                             ViewGroup parent) {
        return getView(mUploadGroups[(int) getGroupId(groupPosition)].items, childPosition, convertView, parent);
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return mUploadGroups[(int) getGroupId(groupPosition)].items.length;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mUploadGroups[(int) getGroupId(groupPosition)];
    }

    @Override
    public int getGroupCount() {
        int size = 0;
        for (UploadGroup uploadGroup : mUploadGroups) {
            if (uploadGroup.items.length > 0) {
                size++;
            }
        }
        return size;
    }

    /**
     * Returns the groupId (that is, index in mUploadGroups) for group at position groupPosition (0-based).
     * Could probably be done more intuitive but this tested methods works as intended.
     */
    @Override
    public long getGroupId(int groupPosition) {
        int id = -1;
        for (int i = 0; i <= groupPosition; ) {
            id++;
            if (mUploadGroups[id].items.length > 0) {
                i++;
            }
        }
        return id;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        //force group to stay unfolded
        ExpandableListView listView = (ExpandableListView) parent;
        listView.expandGroup(groupPosition);

        listView.setGroupIndicator(null);
        UploadGroup group = (UploadGroup) getGroup(groupPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mParentActivity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.upload_list_group, null);
        }
        TextView tvGroupName = (TextView) convertView.findViewById(R.id.uploadListGroupName);
        TextView tvFileCount = (TextView) convertView.findViewById(R.id.textViewFileCount);

        int stringResFileCount = group.getGroupCount() == 1 ? R.string.uploads_view_group_file_count_single :
                R.string.uploads_view_group_file_count;
        String fileCountText = String.format(mParentActivity.getString(stringResFileCount), group.getGroupCount());

        tvGroupName.setText(group.getGroupName());
        tvFileCount.setText(fileCountText);
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public class ProgressListener implements OnDatatransferProgressListener {
        int mLastPercent = 0;
        OCUpload mUpload = null;
        WeakReference<ProgressBar> mProgressBar = null;

        public ProgressListener(OCUpload upload, ProgressBar progressBar) {
            mUpload = upload;
            mProgressBar = new WeakReference<ProgressBar>(progressBar);
        }

        @Override
        public void onTransferProgress(long progressRate, long totalTransferredSoFar, long totalToTransfer, String
                filename) {
            int percent = (int) (100.0 * ((double) totalTransferredSoFar) / ((double) totalToTransfer));
            if (percent != mLastPercent) {
                ProgressBar pb = mProgressBar.get();
                if (pb != null) {
                    pb.setProgress(percent);
                    pb.postInvalidate();
                }
            }
            mLastPercent = percent;
        }

        public boolean isWrapping(ProgressBar progressBar) {
            ProgressBar wrappedProgressBar = mProgressBar.get();
            return (
                wrappedProgressBar != null &&
                wrappedProgressBar == progressBar   // on purpose; don't replace with equals
            );
        }

        public OCUpload getUpload() {
            return mUpload;
        }

    }

    public void addBinder() {
        notifyDataSetChanged();
    }
}