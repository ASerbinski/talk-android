/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.controllers;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.emoji.text.EmojiCompat;
import androidx.emoji.widget.EmojiEditText;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.messages.MagicIncomingTextMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicOutcomingTextMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicPreviewMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicSystemMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicUnreadNoticeMessageViewHolder;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback;
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.UserMentionClickEvent;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.chat.ChatOverall;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.conversations.RoomsOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.presenters.MentionAutocompletePresenter;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ConductorRemapping;
import com.nextcloud.talk.utils.DateUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.KeyboardUtils;
import com.nextcloud.talk.utils.MagicCharPolicy;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.nextcloud.talk.utils.text.Spans;
import com.nextcloud.talk.webrtc.MagicWebSocketInstance;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;
import com.otaliastudios.autocomplete.Autocomplete;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.otaliastudios.autocomplete.AutocompletePresenter;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;
import com.vanniktech.emoji.EmojiImageView;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.emoji.Emoji;
import com.vanniktech.emoji.listeners.OnEmojiClickListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupDismissListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupShownListener;
import com.webianks.library.PopupBubble;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;
import retrofit2.Response;

@AutoInjector(NextcloudTalkApplication.class)
public class ChatController extends BaseController implements MessagesListAdapter.OnLoadMoreListener,
        MessagesListAdapter.Formatter<Date>, MessagesListAdapter.OnMessageLongClickListener, MessageHolders.ContentChecker {
    private static final String TAG = "ChatController";
    private static final byte CONTENT_TYPE_SYSTEM_MESSAGE = 1;
    private static final byte CONTENT_TYPE_UNREAD_NOTICE_MESSAGE = 2;

    @Inject
    NcApi ncApi;
    @Inject
    UserUtils userUtils;
    @Inject
    AppPreferences appPreferences;
    @Inject
    Context context;
    @Inject
    EventBus eventBus;
    @BindView(R.id.messagesListView)
    MessagesList messagesListView;
    @BindView(R.id.messageInputView)
    MessageInput messageInputView;
    @BindView(R.id.messageInput)
    EmojiEditText messageInput;
    @BindView(R.id.popupBubbleView)
    PopupBubble popupBubble;
    @BindView(R.id.progressBar)
    ProgressBar loadingProgressBar;
    @BindView(R.id.smileyButton)
    ImageButton smileyButton;
    @BindView(R.id.lobby_view)
    RelativeLayout lobbyView;
    @BindView(R.id.lobby_text_view)
    TextView conversationLobbyText;
    private List<Disposable> disposableList = new ArrayList<>();
    private String roomToken;
    private UserEntity conversationUser;
    private String roomPassword;
    private String credentials;
    private Conversation currentConversation;
    private boolean inConversation = false;
    private boolean historyRead = false;
    private int globalLastKnownFutureMessageId = -1;
    private int globalLastKnownPastMessageId = -1;
    private MessagesListAdapter<ChatMessage> adapter;
    private Autocomplete mentionAutocomplete;
    private LinearLayoutManager layoutManager;
    private boolean lookingIntoFuture = false;
    private int newMessagesCount = 0;
    private Boolean startCallFromNotification = null;
    private String roomId;
    private boolean voiceOnly;
    private boolean isFirstMessagesProcessing = true;
    private boolean isLeavingForConversation;
    private boolean isLinkPreviewAllowed;
    private boolean wasDetached;
    private EmojiPopup emojiPopup;

    private CharSequence myFirstMessage;
    private boolean checkingLobbyStatus;

    private MenuItem conversationInfoMenuItem;
    private MenuItem conversationVoiceCallMenuItem;
    private MenuItem conversationVideoMenuItem;

    private MagicWebSocketInstance magicWebSocketInstance;

    private Handler lobbyTimerHandler;
    private boolean roomJoined;

    public ChatController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        this.conversationUser = args.getParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY());
        this.roomId = args.getString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), "");
        this.roomToken = args.getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), "");

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION())) {
            this.currentConversation = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION()));
        }

        this.roomPassword = args.getString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), "");

        if (conversationUser.getUserId().equals("?")) {
            credentials = null;
        } else {
            credentials = ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken());
        }

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_FROM_NOTIFICATION_START_CALL())) {
            this.startCallFromNotification = args.getBoolean(BundleKeys.INSTANCE.getKEY_FROM_NOTIFICATION_START_CALL());
        }

        this.voiceOnly = args.getBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), false);
    }

    private void getRoomInfo() {
        boolean shouldRepeat = conversationUser.hasSpreedFeatureCapability("webinary-lobby");
        if (shouldRepeat) {
            checkingLobbyStatus = true;
        }

        ncApi.getRoom(credentials, ApiUtils.getRoom(conversationUser.getBaseUrl(), roomToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposableList.add(d);
                    }

                    @Override
                    public void onNext(RoomOverall roomOverall) {
                        currentConversation = roomOverall.getOcs().getData();

                        loadAvatarForStatusBar();

                        setTitle();
                        setupMentionAutocomplete();
                        checkReadOnlyState();
                        checkLobbyState();

                        if (!inConversation) {
                            joinRoomWithPassword();
                        }

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        if (shouldRepeat) {
                            if (lobbyTimerHandler == null) {
                                lobbyTimerHandler = new Handler();
                            }

                            lobbyTimerHandler.postDelayed(() -> getRoomInfo(), 5000);
                        }
                    }
                });
    }

    private void handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(conversationUser.getBaseUrl()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposableList.add(d);
                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Conversation conversation : roomsOverall.getOcs().getData()) {
                            if (roomId.equals(conversation.getRoomId())) {
                                roomToken = conversation.getToken();
                                currentConversation = conversation;
                                setTitle();
                                getRoomInfo();
                                break;
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_chat, container, false);
    }

    private void loadAvatarForStatusBar() {
        if (currentConversation != null && currentConversation.getType() != null && currentConversation.getType().equals(Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) && getActivity() != null && conversationVoiceCallMenuItem != null) {
            int avatarSize =
                    (int) DisplayUtils.convertDpToPixel(conversationVoiceCallMenuItem.getIcon().getIntrinsicWidth(), getActivity());

            ImageRequest imageRequest =
                    DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithNameAndPixels(conversationUser.getBaseUrl(),
                            currentConversation.getName(), avatarSize / 2), null);

            ImagePipeline imagePipeline = Fresco.getImagePipeline();
            DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);

            dataSource.subscribe(new BaseBitmapDataSubscriber() {
                @Override
                protected void onNewResultImpl(@Nullable Bitmap bitmap) {
                    if (getActionBar() != null && bitmap != null && getResources() != null) {
                        RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                        roundedBitmapDrawable.setCircular(true);
                        roundedBitmapDrawable.setAntiAlias(true);
                        getActionBar().setIcon(roundedBitmapDrawable);
                    }
                }

                @Override
                protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                }
            }, UiThreadImmediateExecutorService.getInstance());
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        getActionBar().show();
        boolean adapterWasNull = false;

        if (adapter == null) {
            loadingProgressBar.setVisibility(View.VISIBLE);

            adapterWasNull = true;

            MessageHolders messageHolders = new MessageHolders();
            messageHolders.setIncomingTextConfig(MagicIncomingTextMessageViewHolder.class, R.layout.item_custom_incoming_text_message);
            messageHolders.setOutcomingTextConfig(MagicOutcomingTextMessageViewHolder.class, R.layout.item_custom_outcoming_text_message);

            messageHolders.setIncomingImageConfig(MagicPreviewMessageViewHolder.class, R.layout.item_custom_incoming_preview_message);
            messageHolders.setOutcomingImageConfig(MagicPreviewMessageViewHolder.class, R.layout.item_custom_outcoming_preview_message);

            messageHolders.registerContentType(CONTENT_TYPE_SYSTEM_MESSAGE, MagicSystemMessageViewHolder.class,
                    R.layout.item_system_message, MagicSystemMessageViewHolder.class, R.layout.item_system_message,
                    this);

            messageHolders.registerContentType(CONTENT_TYPE_UNREAD_NOTICE_MESSAGE,
                    MagicUnreadNoticeMessageViewHolder.class, R.layout.item_date_header,
                    MagicUnreadNoticeMessageViewHolder.class, R.layout.item_date_header, this);

            adapter = new MessagesListAdapter<>(conversationUser.getUserId(), messageHolders, new ImageLoader() {
                @Override
                public void loadImage(SimpleDraweeView imageView, @androidx.annotation.Nullable String url, @androidx.annotation.Nullable Object payload) {
                    DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                            .setImageRequest(DisplayUtils.getImageRequestForUrl(url, conversationUser))
                            .setControllerListener(DisplayUtils.getImageControllerListener(imageView))
                            .setOldController(imageView.getController())
                            .setAutoPlayAnimations(true)
                            .build();
                    imageView.setController(draweeController);
                }
            });
        } else {
            messagesListView.setVisibility(View.VISIBLE);
        }

        messagesListView.setAdapter(adapter);
        adapter.setLoadMoreListener(this);
        adapter.setDateHeadersFormatter(this::format);
        adapter.setOnMessageLongClickListener(this);

        layoutManager = (LinearLayoutManager) messagesListView.getLayoutManager();

        popupBubble.setRecyclerView(messagesListView);

        popupBubble.setPopupBubbleListener(context -> {
            if (newMessagesCount != 0) {
                int scrollPosition;
                if (newMessagesCount - 1 < 0) {
                    scrollPosition = 0;
                } else {
                    scrollPosition = newMessagesCount - 1;
                }
                new Handler().postDelayed(() -> messagesListView.smoothScrollToPosition(scrollPosition), 200);
            }
        });

        messagesListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (newMessagesCount != 0) {
                        if (layoutManager.findFirstCompletelyVisibleItemPosition() < newMessagesCount) {
                            newMessagesCount = 0;

                            if (popupBubble != null && popupBubble.isShown()) {
                                popupBubble.hide();
                            }
                        }
                    }
                }
            }
        });


        InputFilter[] filters = new InputFilter[1];
        int lengthFilter = conversationUser.getMessageMaxLength();


        filters[0] = new InputFilter.LengthFilter(lengthFilter);
        messageInput.setFilters(filters);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= lengthFilter) {
                    messageInput.setError(String.format(Objects.requireNonNull(getResources()).getString(R.string.nc_limit_hit), Integer.toString(lengthFilter)));
                } else {
                    messageInput.setError(null);
                }

                Editable editable = messageInput.getEditableText();
                Spans.MentionChipSpan[] mentionSpans = editable.getSpans(0, messageInput.length(),
                        Spans.MentionChipSpan.class);
                Spans.MentionChipSpan mentionSpan;
                for (int i = 0; i < mentionSpans.length; i++) {
                    mentionSpan = mentionSpans[i];
                    if (start >= editable.getSpanStart(mentionSpan) && start < editable.getSpanEnd(mentionSpan)) {
                        if (!editable.subSequence(editable.getSpanStart(mentionSpan),
                                editable.getSpanEnd(mentionSpan)).toString().trim().equals(mentionSpan.getLabel())) {
                            editable.removeSpan(mentionSpan);
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        messageInputView.setAttachmentsListener(new MessageInput.AttachmentsListener() {
            @Override
            public void onAddAttachments() {
                showBrowserScreen(BrowserController.BrowserType.DAV_BROWSER);
            }
        });

        messageInputView.getButton().setOnClickListener(v -> submitMessage());
        messageInputView.getButton().setContentDescription(getResources()
                .getString(R.string.nc_description_send_message_button));

        if (currentConversation != null && currentConversation.getRoomId() != null) {
            loadAvatarForStatusBar();
            setTitle();
        }

        if (adapterWasNull) {
            // we're starting
            if (TextUtils.isEmpty(roomToken)) {
                handleFromNotification();
            } else {
                getRoomInfo();
            }
        }
    }


    private void checkReadOnlyState() {
        if (currentConversation != null) {
            if (currentConversation.shouldShowLobby(conversationUser) || (currentConversation.getConversationReadOnlyState() != null && currentConversation.getConversationReadOnlyState().equals(Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY))) {

                conversationVoiceCallMenuItem.getIcon().setAlpha(99);
                conversationVideoMenuItem.getIcon().setAlpha(99);
                if (messageInputView != null) {
                    messageInputView.setVisibility(View.GONE);
                }

            } else {
                if (conversationVoiceCallMenuItem != null) {
                    conversationVoiceCallMenuItem.getIcon().setAlpha(255);
                }

                if (conversationVideoMenuItem != null) {
                    conversationVideoMenuItem.getIcon().setAlpha(255);
                }

                if (messageInputView != null) {
                    if (currentConversation.shouldShowLobby(conversationUser)) {
                        messageInputView.setVisibility(View.GONE);
                    } else {
                        messageInputView.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
    }

    private void checkLobbyState() {
        if (currentConversation != null && currentConversation.isLobbyViewApplicable(conversationUser)) {

            if (!checkingLobbyStatus) {
                getRoomInfo();
            }

            if (currentConversation.shouldShowLobby(conversationUser)) {
                if (lobbyView != null) {
                    lobbyView.setVisibility(View.VISIBLE);
                }
                messagesListView.setVisibility(View.GONE);
                messageInputView.setVisibility(View.GONE);
                loadingProgressBar.setVisibility(View.GONE);

                if (currentConversation.getLobbyTimer() != null && currentConversation.getLobbyTimer() != 0L) {
                    conversationLobbyText.setText(String.format(getResources().getString(R.string.nc_lobby_waiting_with_date), DateUtils.INSTANCE.getLocalDateStringFromTimestampForLobby(currentConversation.getLobbyTimer())));
                } else {
                    conversationLobbyText.setText(R.string.nc_lobby_waiting);
                }
            } else {
                if (lobbyView != null) {
                    lobbyView.setVisibility(View.GONE);
                }
                messagesListView.setVisibility(View.VISIBLE);
                messageInput.setVisibility(View.VISIBLE);
            }
        } else {
            if (lobbyView != null) {
                lobbyView.setVisibility(View.GONE);
            }
            messagesListView.setVisibility(View.VISIBLE);
            messageInput.setVisibility(View.VISIBLE);
        }
    }

    private void showBrowserScreen(BrowserController.BrowserType browserType) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_BROWSER_TYPE(), Parcels.wrap(browserType));
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), Parcels.wrap(conversationUser));
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomToken);
        getRouter().pushController((RouterTransaction.with(new BrowserController(bundle))
                .pushChangeHandler(new VerticalChangeHandler())
                .popChangeHandler(new VerticalChangeHandler())));
    }

    private void showConversationInfoScreen() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), conversationUser);
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomToken);
        getRouter().pushController((RouterTransaction.with(new ConversationInfoController(bundle))
                .pushChangeHandler(new HorizontalChangeHandler())
                .popChangeHandler(new HorizontalChangeHandler())));
    }

    private void setupMentionAutocomplete() {
        float elevation = 6f;
        Drawable backgroundDrawable = new ColorDrawable(getResources().getColor(R.color.bg_default));
        AutocompletePresenter<Mention> presenter = new MentionAutocompletePresenter(getApplicationContext(), roomToken);
        AutocompleteCallback<Mention> callback = new MentionAutocompleteCallback(getActivity(),
                conversationUser, messageInput);

        if (mentionAutocomplete == null && messageInput != null) {
            mentionAutocomplete = Autocomplete.<Mention>on(messageInput)
                    .with(elevation)
                    .with(backgroundDrawable)
                    .with(new MagicCharPolicy('@'))
                    .with(presenter)
                    .with(callback)
                    .build();
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);

        if (!conversationUser.getUserId().equals("?") && conversationUser.hasSpreedFeatureCapability("mention-flag") && getActivity() != null) {
            getActivity().findViewById(R.id.toolbar).setOnClickListener(v -> showConversationInfoScreen());
        }

        isLeavingForConversation = false;
        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomId(roomId);
        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomToken(roomId);
        ApplicationWideCurrentRoomHolder.getInstance().setInCall(false);
        ApplicationWideCurrentRoomHolder.getInstance().setUserInRoom(conversationUser);

        isLinkPreviewAllowed = appPreferences.getAreLinkPreviewsAllowed();

        emojiPopup = EmojiPopup.Builder.fromRootView(view).setOnEmojiPopupShownListener(new OnEmojiPopupShownListener() {
            @Override
            public void onEmojiPopupShown() {
                if (getResources() != null) {
                    smileyButton.setColorFilter(getResources().getColor(R.color.colorPrimary),
                            PorterDuff.Mode.SRC_IN);
                }
            }
        }).setOnEmojiPopupDismissListener(new OnEmojiPopupDismissListener() {
            @Override
            public void onEmojiPopupDismiss() {
                if (smileyButton != null) {
                    smileyButton.setColorFilter(getResources().getColor(R.color.emoji_icons),
                            PorterDuff.Mode.SRC_IN);
                }
            }
        }).setOnEmojiClickListener(new OnEmojiClickListener() {
            @Override
            public void onEmojiClick(@NonNull EmojiImageView emoji, @NonNull Emoji imageView) {
                messageInput.getEditableText().append(" ");
            }
        }).build(messageInput);

        if (getActivity() != null) {
            new KeyboardUtils(getActivity(), getView(), false);
        }

        cancelNotificationsForCurrentConversation();

        if (inConversation) {
            if (wasDetached && conversationUser.hasSpreedFeatureCapability("no-ping")) {
                currentConversation.setSessionId("0");
                wasDetached = false;
                joinRoomWithPassword();
            }
        }
    }

    private void cancelNotificationsForCurrentConversation() {
        if (!conversationUser.hasSpreedFeatureCapability("no-ping") && !TextUtils.isEmpty(roomId)) {
            NotificationUtils.INSTANCE.cancelExistingNotificationsForRoom(getApplicationContext(), conversationUser, roomId);
        } else if (!TextUtils.isEmpty(roomToken)) {
            NotificationUtils.INSTANCE.cancelExistingNotificationsForRoom(getApplicationContext(), conversationUser, roomToken);
        }
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        ApplicationWideCurrentRoomHolder.getInstance().clear();
        eventBus.unregister(this);

        if (getActivity() != null) {
            getActivity().findViewById(R.id.toolbar).setOnClickListener(null);
        }

        if (conversationUser.hasSpreedFeatureCapability("no-ping")
                && getActivity() != null && !getActivity().isChangingConfigurations() && !isLeavingForConversation) {
            wasDetached = true;
            leaveRoom();
        }

        if (mentionAutocomplete != null && mentionAutocomplete.isPopupShowing()) {
            mentionAutocomplete.dismissPopup();
        }
    }

    @Override
    protected String getTitle() {
        if (currentConversation != null) {
            return String.valueOf(EmojiCompat.get().process(currentConversation.getDisplayName()));
        } else {
            return "";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (getActivity() != null) {
            getActivity().findViewById(R.id.toolbar).setOnClickListener(null);
        }

        if (getActionBar() != null) {
            getActionBar().setIcon(null);
        }

        adapter = null;
        inConversation = false;
    }

    private void dispose() {
        Disposable disposable;
        for (int i = 0; i < disposableList.size(); i++) {
            if (!(disposable = disposableList.get(i)).isDisposed()) {
                disposable.dispose();
            }
        }
    }

    private void startPing() {
        if (!conversationUser.hasSpreedFeatureCapability("no-ping")) {
            ncApi.pingCall(credentials, ApiUtils.getUrlForCallPing(conversationUser.getBaseUrl(), roomToken))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .repeatWhen(observable -> observable.delay(5000, TimeUnit.MILLISECONDS))
                    .takeWhile(observable -> inConversation)
                    .retry(3, observable -> inConversation)
                    .subscribe(new Observer<GenericOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            disposableList.add(d);
                        }

                        @Override
                        public void onNext(GenericOverall genericOverall) {

                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        }
    }

    @OnClick(R.id.smileyButton)
    void onSmileyClick() {
        emojiPopup.toggle();
    }

    private void joinRoomWithPassword() {

        if (currentConversation == null || TextUtils.isEmpty(currentConversation.getSessionId()) || currentConversation.getSessionId().equals("0")) {
            ncApi.joinRoom(credentials,
                    ApiUtils.getUrlForSettingMyselfAsActiveParticipant(conversationUser.getBaseUrl(), roomToken), roomPassword)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3)
                    .subscribe(new Observer<RoomOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            disposableList.add(d);
                        }

                        @Override
                        public void onNext(RoomOverall roomOverall) {
                            inConversation = true;
                            currentConversation = roomOverall.getOcs().getData();
                            setTitle();
                            ApplicationWideCurrentRoomHolder.getInstance().setSession(currentConversation.getSessionId());
                            startPing();

                            setupWebsocket();
                            checkLobbyState();

                            if (isFirstMessagesProcessing) {
                                pullChatMessages(0);
                            } else {
                                pullChatMessages(1);
                            }

                            if (magicWebSocketInstance != null) {
                                magicWebSocketInstance.joinRoomWithRoomTokenAndSession(roomToken, currentConversation.getSessionId());
                            }
                            if (startCallFromNotification != null && startCallFromNotification) {
                                startCallFromNotification = false;
                                startACall(voiceOnly);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else {
            inConversation = true;
            ApplicationWideCurrentRoomHolder.getInstance().setSession(currentConversation.getSessionId());
            if (magicWebSocketInstance != null) {
                magicWebSocketInstance.joinRoomWithRoomTokenAndSession(roomToken,
                        currentConversation.getSessionId());
            }
            startPing();
            if (isFirstMessagesProcessing) {
                pullChatMessages(0);
            } else {
                pullChatMessages(1);
            }
        }
    }

    private void leaveRoom() {
        ncApi.leaveRoom(credentials,
                ApiUtils.getUrlForSettingMyselfAsActiveParticipant(conversationUser.getBaseUrl(),
                        roomToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposableList.add(d);
                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        checkingLobbyStatus = false;

                        if (lobbyTimerHandler != null) {
                            lobbyTimerHandler.removeCallbacksAndMessages(null);
                        }

                        if (magicWebSocketInstance != null && currentConversation != null) {
                            magicWebSocketInstance.joinRoomWithRoomTokenAndSession("", currentConversation.getSessionId());
                        }

                        if (!isDestroyed() && !isBeingDestroyed() && !wasDetached) {
                            getRouter().popCurrentController();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        dispose();
                    }
                });
    }

    private void setSenderId() {
        try {
            final Field senderId = adapter.getClass().getDeclaredField("senderId");
            senderId.setAccessible(true);
            senderId.set(adapter, conversationUser.getUserId());
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "Failed to set sender id");
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to access and set field");
        }
    }

    private void submitMessage() {
        final Editable editable = messageInput.getEditableText();
        Spans.MentionChipSpan mentionSpans[] = editable.getSpans(0, editable.length(),
                Spans.MentionChipSpan.class);
        Spans.MentionChipSpan mentionSpan;
        for (int i = 0; i < mentionSpans.length; i++) {
            mentionSpan = mentionSpans[i];
            String mentionId = mentionSpan.getId();
            if (mentionId.contains(" ") || mentionId.startsWith("guest/")) {
                mentionId = "\"" + mentionId + "\"";
            }
            editable.replace(editable.getSpanStart(mentionSpan), editable.getSpanEnd(mentionSpan), "@" + mentionId);
        }

        messageInput.setText("");
        sendMessage(editable);
    }

    private void sendMessage(CharSequence message) {

        ncApi.sendChatMessage(credentials, ApiUtils.getUrlForChat(conversationUser.getBaseUrl(), roomToken),
                message, conversationUser.getDisplayName())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        myFirstMessage = message;

                        if (popupBubble != null && popupBubble.isShown()) {
                            popupBubble.hide();
                        }

                        if (messagesListView != null) {
                            messagesListView.smoothScrollToPosition(0);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (e instanceof HttpException) {
                            int code = ((HttpException) e).code();
                            if (Integer.toString(code).startsWith("2")) {
                                myFirstMessage = message;

                                if (popupBubble != null && popupBubble.isShown()) {
                                    popupBubble.hide();
                                }

                                messagesListView.smoothScrollToPosition(0);
                            }
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void setupWebsocket() {
        if (WebSocketConnectionHelper.getMagicWebSocketInstanceForUserId(conversationUser.getId()) != null) {
            magicWebSocketInstance = WebSocketConnectionHelper.getMagicWebSocketInstanceForUserId(conversationUser.getId());
        } else {
            magicWebSocketInstance = null;
        }
    }

    private void pullChatMessages(int lookIntoFuture) {
        if (!inConversation) {
            return;
        }

        if (currentConversation.shouldShowLobby(conversationUser)) {
            return;
        }

        Map<String, Integer> fieldMap = new HashMap<>();
        fieldMap.put("includeLastKnown", 0);

        int timeout = 30;
        if (lookIntoFuture == 0) {
            timeout = 0;
        }

        fieldMap.put("timeout", timeout);

        if (lookIntoFuture > 0) {
            lookingIntoFuture = true;
        } else if (isFirstMessagesProcessing) {
            globalLastKnownFutureMessageId = currentConversation.getLastReadMessage();
            globalLastKnownPastMessageId = currentConversation.getLastReadMessage();
            fieldMap.put("includeLastKnown", 1);
        }

        fieldMap.put("lookIntoFuture", lookIntoFuture);
        fieldMap.put("limit", 100);
        fieldMap.put("setReadMarker", 1);

        int lastKnown;
        if (lookIntoFuture > 0) {
            lastKnown = globalLastKnownFutureMessageId;
        } else {
            lastKnown = globalLastKnownPastMessageId;
        }

        fieldMap.put("lastKnownMessageId", lastKnown);

        if (!wasDetached) {
            if (lookIntoFuture > 0) {
                int finalTimeout = timeout;
                ncApi.pullChatMessages(credentials, ApiUtils.getUrlForChat(conversationUser.getBaseUrl(),
                        roomToken),
                        fieldMap)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .takeWhile(observable -> inConversation && !wasDetached)
                        .subscribe(new Observer<Response>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                disposableList.add(d);
                            }

                            @Override
                            public void onNext(Response response) {
                                processMessages(response, true, finalTimeout);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });

            } else {
                ncApi.pullChatMessages(credentials,
                        ApiUtils.getUrlForChat(conversationUser.getBaseUrl(), roomToken), fieldMap)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(3, observable -> inConversation && !wasDetached)
                        .takeWhile(observable -> inConversation && !wasDetached)
                        .subscribe(new Observer<Response>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                disposableList.add(d);
                            }

                            @Override
                            public void onNext(Response response) {
                                processMessages(response, false, 0);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }
        }
    }

    private void processMessages(Response response, boolean isFromTheFuture, int timeout) {
        String xChatLastGivenHeader;
        if (response.headers().size() > 0 && !TextUtils.isEmpty((xChatLastGivenHeader = response.headers().get("X-Chat-Last-Given")))) {

            int header = Integer.parseInt(xChatLastGivenHeader);
            if (header > 0) {
                if (isFromTheFuture) {
                    globalLastKnownFutureMessageId = header;
                } else {
                    globalLastKnownPastMessageId = header;
                }
            }
        }

        if (response.code() == 200) {

            ChatOverall chatOverall = (ChatOverall) response.body();
            List<ChatMessage> chatMessageList = chatOverall.getOcs().getData();

            boolean wasFirstMessageProcessing = isFirstMessagesProcessing;

            if (isFirstMessagesProcessing) {
                cancelNotificationsForCurrentConversation();

                isFirstMessagesProcessing = false;
                if (loadingProgressBar != null) {
                    loadingProgressBar.setVisibility(View.GONE);
                }

                if (messagesListView != null) {
                    messagesListView.setVisibility(View.VISIBLE);
                }

            }

            int countGroupedMessages = 0;
            if (!isFromTheFuture) {

                for (int i = 0; i < chatMessageList.size(); i++) {
                    if (chatMessageList.size() > i + 1) {
                        if (TextUtils.isEmpty(chatMessageList.get(i).getSystemMessage()) &&
                                TextUtils.isEmpty(chatMessageList.get(i + 1).getSystemMessage()) &&
                                chatMessageList.get(i + 1).getActorId().equals(chatMessageList.get(i).getActorId()) &&
                                countGroupedMessages < 4 && DateFormatter.isSameDay(chatMessageList.get(i).getCreatedAt(),
                                chatMessageList.get(i + 1).getCreatedAt())) {
                            chatMessageList.get(i).setGrouped(true);
                            countGroupedMessages++;
                        } else {
                            countGroupedMessages = 0;
                        }
                    }

                    ChatMessage chatMessage = chatMessageList.get(i);
                    chatMessage.setOneToOneConversation(currentConversation.getType().equals(Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL));
                    chatMessage.setLinkPreviewAllowed(isLinkPreviewAllowed);
                    chatMessage.setActiveUser(conversationUser);
                }

                if (wasFirstMessageProcessing) {
                    globalLastKnownFutureMessageId = chatMessageList.get(0).getJsonMessageId();
                }

                if (adapter != null) {
                    adapter.addToEnd(chatMessageList, false);
                }

            } else {

                ChatMessage chatMessage;

                boolean shouldAddNewMessagesNotice =
                        timeout == 0 && adapter.getItemCount() > 0 && chatMessageList.size() > 0;

                if (shouldAddNewMessagesNotice) {
                    ChatMessage unreadChatMessage = new ChatMessage();
                    unreadChatMessage.setJsonMessageId(-1);
                    unreadChatMessage.setActorId("-1");
                    unreadChatMessage.setTimestamp(chatMessageList.get(0).getTimestamp());
                    unreadChatMessage.setMessage(context.getString(R.string.nc_new_messages));
                    adapter.addToStart(unreadChatMessage, false);
                }

                boolean isThereANewNotice = shouldAddNewMessagesNotice || adapter.getMessagePositionByIdInReverse("-1") != -1;

                for (int i = 0; i < chatMessageList.size(); i++) {
                    chatMessage = chatMessageList.get(i);

                    chatMessage.setActiveUser(conversationUser);
                    chatMessage.setLinkPreviewAllowed(isLinkPreviewAllowed);

                    if (chatMessage.getJsonMessageId() > globalLastKnownFutureMessageId) {
                        globalLastKnownFutureMessageId = chatMessage.getJsonMessageId();
                    }

                    // if credentials are empty, we're acting as a guest
                    if (TextUtils.isEmpty(credentials) && myFirstMessage != null && !TextUtils.isEmpty(myFirstMessage.toString())) {
                        if (chatMessage.getActorType().equals("guests")) {
                            conversationUser.setUserId(chatMessage.getActorId());
                            setSenderId();
                        }
                    }

                    boolean shouldScroll =
                            !isThereANewNotice && layoutManager.findFirstVisibleItemPosition() == 0 ||
                            (adapter != null && adapter.getItemCount() == 0);

                    if (!shouldAddNewMessagesNotice && !shouldScroll && popupBubble != null) {
                        if (!popupBubble.isShown()) {
                            newMessagesCount = 1;
                            popupBubble.show();
                        } else if (popupBubble.isShown()) {
                            newMessagesCount++;
                        }
                    } else {
                        newMessagesCount = 0;
                    }

                    if (adapter != null) {
                        chatMessage.setGrouped(adapter.isPreviousSameAuthor(chatMessage.getActorId(), -1) && (adapter.getSameAuthorLastMessagesCount(chatMessage.getActorId()) % 5) > 0);
                        chatMessage.setOneToOneConversation(currentConversation.getType().equals(Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL));
                        adapter.addToStart(chatMessage, shouldScroll);
                    }

                }

                if (shouldAddNewMessagesNotice && adapter != null) {
                    layoutManager.scrollToPositionWithOffset(adapter.getMessagePositionByIdInReverse("-1"), messagesListView.getHeight() / 2);
                }

            }

            if (inConversation) {
                pullChatMessages(1);
            }
        } else if (response.code() == 304 && !isFromTheFuture) {
                if (isFirstMessagesProcessing) {
                    cancelNotificationsForCurrentConversation();

                    isFirstMessagesProcessing = false;
                    if (loadingProgressBar != null) {
                        loadingProgressBar.setVisibility(View.GONE);
                    }
                }

                historyRead = true;

            if (!lookingIntoFuture && inConversation) {
                pullChatMessages(1);
            }
        }
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {
        if (!historyRead && inConversation) {
            pullChatMessages(0);
        }
    }


    @Override
    public String format(Date date) {
        if (DateFormatter.isToday(date)) {
            return getResources().getString(R.string.nc_date_header_today);
        } else if (DateFormatter.isYesterday(date)) {
            return getResources().getString(R.string.nc_date_header_yesterday);
        } else {
            return DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_conversation, menu);
        if (conversationUser.getUserId().equals("?")) {
            menu.removeItem(R.id.conversation_info);
        } else {
            conversationInfoMenuItem = menu.findItem(R.id.conversation_info);
            conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call);
            conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call);

            loadAvatarForStatusBar();
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (conversationUser.hasSpreedFeatureCapability("read-only-rooms")) {
            checkReadOnlyState();
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getRouter().popCurrentController();
                return true;
            case R.id.conversation_video_call:
                if (conversationVideoMenuItem.getIcon().getAlpha() == 255) {
                    startACall(false);
                    return true;
                }
                return false;
            case R.id.conversation_voice_call:
                if (conversationVoiceCallMenuItem.getIcon().getAlpha() == 255) {
                    startACall(true);
                    return true;
                }
                return false;
            case R.id.conversation_info:
                showConversationInfoScreen();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startACall(boolean isVoiceOnlyCall) {
        isLeavingForConversation = true;
        if (!isVoiceOnlyCall) {
            Intent videoCallIntent = getIntentForCall(false);
            if (videoCallIntent != null) {
                startActivity(videoCallIntent);
            }
        } else {
            Intent voiceCallIntent = getIntentForCall(true);
            if (voiceCallIntent != null) {
                startActivity(voiceCallIntent);
            }
        }
    }

    private Intent getIntentForCall(boolean isVoiceOnlyCall) {
        if (currentConversation != null) {
            Bundle bundle = new Bundle();
            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomToken);
            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), roomId);
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), conversationUser);
            bundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), roomPassword);
            bundle.putString(BundleKeys.INSTANCE.getKEY_MODIFIED_BASE_URL(), conversationUser.getBaseUrl());

            if (isVoiceOnlyCall) {
                bundle.putBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), true);
            }

            if (getActivity() != null) {
                Intent callIntent = new Intent(getActivity(), MagicCallActivity.class);
                callIntent.putExtras(bundle);

                return callIntent;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public void onMessageLongClick(IMessage message) {
        if (getActivity() != null) {
            ClipboardManager clipboardManager = (android.content.ClipboardManager)
                    getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = android.content.ClipData.newPlainText(
                    getResources().getString(R.string.nc_app_name), message.getText());
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(clipData);
            }
        }
    }

    @Override
    public boolean hasContentFor(IMessage message, byte type) {
        switch (type) {
            case CONTENT_TYPE_SYSTEM_MESSAGE:
                return !TextUtils.isEmpty(message.getSystemMessage());
            case CONTENT_TYPE_UNREAD_NOTICE_MESSAGE:
                return message.getId().equals("-1");
        }

        return false;
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(WebSocketCommunicationEvent webSocketCommunicationEvent) {
        /*
        switch (webSocketCommunicationEvent.getType()) {
            case "refreshChat":

                if (webSocketCommunicationEvent.getHashMap().get(BundleKeys.KEY_INTERNAL_USER_ID).equals(Long.toString(conversationUser.getId()))) {
                    if (roomToken.equals(webSocketCommunicationEvent.getHashMap().get(BundleKeys.KEY_ROOM_TOKEN))) {
                        pullChatMessages(2);
                    }
                }
                break;
            default:
        }*/
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(UserMentionClickEvent userMentionClickEvent) {
        if ((!currentConversation.getType().equals(Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) || !currentConversation.getName().equals(userMentionClickEvent.getUserId()))) {
            RetrofitBucket retrofitBucket =
                    ApiUtils.getRetrofitBucketForCreateRoom(conversationUser.getBaseUrl(), "1",
                            userMentionClickEvent.getUserId(), null);

            ncApi.createRoom(credentials,
                    retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<RoomOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(RoomOverall roomOverall) {
                            Intent conversationIntent = new Intent(getActivity(), MagicCallActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), conversationUser);
                            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomOverall.getOcs().getData().getToken());
                            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), roomOverall.getOcs().getData().getRoomId());

                            if (conversationUser.hasSpreedFeatureCapability("chat-v2")) {
                                bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION(),
                                        Parcels.wrap(roomOverall.getOcs().getData()));
                                conversationIntent.putExtras(bundle);

                                ConductorRemapping.INSTANCE.remapChatController(getRouter(), conversationUser.getId(),
                                        roomOverall.getOcs().getData().getToken(), bundle, false);

                            } else {
                                conversationIntent.putExtras(bundle);
                                startActivity(conversationIntent);
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isDestroyed() && !isBeingDestroyed()) {
                                            getRouter().popCurrentController();
                                        }
                                    }
                                }, 100);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        }
    }
}
