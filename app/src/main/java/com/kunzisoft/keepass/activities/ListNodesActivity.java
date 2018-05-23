/*
 * Copyright 2017 Brian Pellin, Jeremy Jamet / Kunzisoft.
 *     
 * This file is part of KeePass DX.
 *
 *  KeePass DX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePass DX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePass DX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.activities;

import android.annotation.SuppressLint;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.kunzisoft.keepass.R;
import com.kunzisoft.keepass.adapters.NodeAdapter;
import com.kunzisoft.keepass.app.App;
import com.kunzisoft.keepass.autofill.AutofillHelper;
import com.kunzisoft.keepass.database.Database;
import com.kunzisoft.keepass.database.PwDatabase;
import com.kunzisoft.keepass.database.PwEntry;
import com.kunzisoft.keepass.database.PwGroup;
import com.kunzisoft.keepass.database.PwNode;
import com.kunzisoft.keepass.database.SortNodeEnum;
import com.kunzisoft.keepass.database.action.AfterActionNodeOnFinish;
import com.kunzisoft.keepass.database.action.OnFinishRunnable;
import com.kunzisoft.keepass.dialogs.AssignMasterKeyDialogFragment;
import com.kunzisoft.keepass.dialogs.SortDialogFragment;
import com.kunzisoft.keepass.password.AssignPasswordHelper;
import com.kunzisoft.keepass.tasks.SaveDatabaseProgressTaskDialogFragment;
import com.kunzisoft.keepass.tasks.UIToastTask;
import com.kunzisoft.keepass.utils.MenuUtil;

public abstract class ListNodesActivity extends LockingActivity
		implements AssignMasterKeyDialogFragment.AssignPasswordDialogListener,
        NodeAdapter.NodeClickCallback,
        SortDialogFragment.SortSelectionListener {

	protected static final String LIST_NODES_FRAGMENT_TAG = "LIST_NODES_FRAGMENT_TAG";
	protected ListNodesFragment listNodesFragment;

	protected PwGroup mCurrentGroup;

    protected AutofillHelper autofillHelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        if ( isFinishing() ) {
            return;
        }
		
		// Likely the app has been killed exit the activity 
		if ( ! App.getDB().getLoaded() ) {
			finish();
			return;
		}

        invalidateOptionsMenu();

        mCurrentGroup = initializeListNodesFragment();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            autofillHelper = new AutofillHelper();
            autofillHelper.retrieveAssistStructure(getIntent());
        }
	}

	protected abstract PwGroup initializeListNodesFragment();

    /**
     * Attach the fragment's list of node.
     * <br />
     * <strong>R.id.nodes_list_fragment_container</strong> must be the id of the container
     */
	protected void attachFragmentToContentView() {
        getSupportFragmentManager().beginTransaction().replace(
                R.id.nodes_list_fragment_container,
                listNodesFragment,
                LIST_NODES_FRAGMENT_TAG)
                .commit();
    }

    public void assignToolbarElements() {
        if (mCurrentGroup != null) {
            String title = mCurrentGroup.getName();
            TextView tv = findViewById(R.id.group_name);
            if (title != null && title.length() > 0) {
                if (tv != null) {
                    tv.setText(title);
                    tv.invalidate();
                }
            } else {
                if (tv != null) {
                    tv.setText(getText(R.string.root));
                    tv.invalidate();
                }
            }
        }
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuInflater inflater = getMenuInflater();
		MenuUtil.contributionMenuInflater(inflater, menu);
		inflater.inflate(R.menu.default_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch ( item.getItemId() ) {
            default:
                // Check the time lock before launching settings
                MenuUtil.onDefaultMenuOptionsItemSelected(this, item, true);
                return super.onOptionsItemSelected(item);
		}
	}

    @Override
    public void onNodeClick(PwNode node) {

        // Add event when we have Autofill
        AssistStructure assistStructure = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assistStructure = autofillHelper.getAssistStructure();
            if (assistStructure != null) {
                switch (node.getType()) {
                    case GROUP:
                        openGroup((PwGroup) node);
                        break;
                    case ENTRY:
                        // Build response with the entry selected
                        autofillHelper.buildResponseWhenEntrySelected(this, (PwEntry) node);
                        finish();
                        break;
                }
            }
        }
        if ( assistStructure == null ){
            switch (node.getType()) {
                case GROUP:
                    openGroup((PwGroup) node);
                    break;
                case ENTRY:
                    EntryActivity.launch(this, (PwEntry) node);
                    break;
            }
        }
    }

    private void openGroup(PwGroup group) {
        ListNodesFragment newListNodeFragment = ListNodesFragment.newInstance(group.getId());
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.nodes_list_fragment_container,
                        newListNodeFragment,
                        LIST_NODES_FRAGMENT_TAG)
                .addToBackStack(LIST_NODES_FRAGMENT_TAG)
                .commit();
        listNodesFragment = newListNodeFragment;
        mCurrentGroup = group;
        assignToolbarElements();
    }

    @Override
    public void onAssignKeyDialogPositiveClick(
    		boolean masterPasswordChecked, String masterPassword,
			boolean keyFileChecked, Uri keyFile) {

        AssignPasswordHelper assignPasswordHelper =
                new AssignPasswordHelper(this,
                        masterPasswordChecked, masterPassword, keyFileChecked, keyFile);
        assignPasswordHelper.assignPasswordInDatabase(null);
    }

    @Override
    public void onAssignKeyDialogNegativeClick(
			boolean masterPasswordChecked, String masterPassword,
			boolean keyFileChecked, Uri keyFile) {

    }

    @Override
    public void onSortSelected(SortNodeEnum sortNodeEnum, boolean ascending, boolean groupsBefore, boolean recycleBinBottom) {
        if (listNodesFragment != null)
            listNodesFragment.onSortSelected(sortNodeEnum, ascending, groupsBefore, recycleBinBottom);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillHelper.onActivityResultSetResultAndFinish(this, requestCode, resultCode, data);
        }
    }

	@SuppressLint("RestrictedApi")
    @Override
	public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
		/*
		 * ACTION_SEARCH automatically forces a new task. This occurs when you open a kdb file in
		 * another app such as Files or GoogleDrive and then Search for an entry. Here we remove the
		 * FLAG_ACTIVITY_NEW_TASK flag bit allowing search to open it's activity in the current task.
		 */
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			int flags = intent.getFlags();
			flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
			intent.setFlags(flags);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			super.startActivityForResult(intent, requestCode, options);
		}
	}

    class AfterAddNode extends AfterActionNodeOnFinish {
        AfterAddNode(Handler handler) {
            super(handler);
        }

        public void run(PwNode oldNode, PwNode newNode) {
            super.run();

            runOnUiThread(() -> {
                if (mSuccess) {
                	if (listNodesFragment != null)
						listNodesFragment.addNode(newNode);
                } else {
                    displayMessage(ListNodesActivity.this);
                }

                SaveDatabaseProgressTaskDialogFragment.stop(ListNodesActivity.this);
            });
        }
    }

    class AfterUpdateNode extends AfterActionNodeOnFinish {
        AfterUpdateNode(Handler handler) {
            super(handler);
        }

        public void run(PwNode oldNode, PwNode newNode) {
            super.run();

            runOnUiThread(() -> {
                if (mSuccess) {
					if (listNodesFragment != null)
						listNodesFragment.updateNode(oldNode, newNode);
                } else {
                    displayMessage(ListNodesActivity.this);
                }

                SaveDatabaseProgressTaskDialogFragment.stop(ListNodesActivity.this);
            });
        }
    }

    class AfterDeleteNode extends OnFinishRunnable {
        private PwNode pwNode;

        AfterDeleteNode(Handler handler, PwNode pwNode) {
            super(handler);
            this.pwNode = pwNode;
        }

        @Override
        public void run() {
            super.run();

            runOnUiThread(() -> {
                if ( mSuccess) {

					if (listNodesFragment != null)
						listNodesFragment.removeNode(pwNode);

                    PwGroup parent = pwNode.getParent();
                    Database db = App.getDB();
                    PwDatabase database = db.getPwDatabase();
                    if (db.isRecycleBinAvailable() &&
                            db.isRecycleBinEnabled()) {
                        PwGroup recycleBin = database.getRecycleBin();
                        // Add trash if it doesn't exists
                        if (parent.equals(recycleBin)
                                && mCurrentGroup != null
                                && mCurrentGroup.getParent() == null
                                && !mCurrentGroup.equals(recycleBin)) {

							if (listNodesFragment != null)
								listNodesFragment.addNode(parent);
                        }
                    }
                } else {
                    mHandler.post(new UIToastTask(ListNodesActivity.this, "Unrecoverable error: " + mMessage));
                    App.setShutdown();
                    finish();
                }

                SaveDatabaseProgressTaskDialogFragment.stop(ListNodesActivity.this);
            });
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        listNodesFragment = (ListNodesFragment) getSupportFragmentManager().findFragmentByTag(LIST_NODES_FRAGMENT_TAG);
        // to refresh fragment
        listNodesFragment.onResume();
        mCurrentGroup = listNodesFragment.getMainGroup();
        assignToolbarElements();
    }
}
