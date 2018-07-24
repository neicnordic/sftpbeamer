var host1_table;
var host2_table;
var server_info;

var transfer_target;

//host-specific configuration info from file.
var host_info;

//variables for uploading files
var upload_target;
var uploaded_files_array;
var upload_progress_bar_group;
var uploaded_files_number;
var successful_uploaded_files_number;

var current_ajax_request;

function ajaxAsyncCall(settings) {
    if (current_ajax_request) {
        current_ajax_request.abort();
        current_ajax_request = null;
    }
    current_ajax_request = $.ajax(settings);
}

function downloadFile(url) {
    $.ajax({
        type: "GET",
        url: "/sftp/download/check?" + url.substring(url.indexOf('&') + 1),
        statusCode: {
            200: function () {
                var iframe = document.getElementById('download_iframe');
                iframe.src = url;
            },
            404: function () {
                var source = url.substring(url.indexOf('source=') + 7);
                var username = $('#' + source + '-username').val();
                var hostname = $('#' + source + '-hostname').val();
                var port = $('#' + source + '-port').val();
                var is_otp = host_info['otp_hosts'].includes(hostname);

                if (is_otp) {
                    $('#input_credential_modal .modal-body').html('<p>In order to download data, you need to reconnect to host <b id="host-for-connection"></b> again.</p>' +
                        ' <div class="form-group">' +
                        ' <label for="password-for-connection" class="sr-only">Password</label>' +
                        ' <input type="password" class="form-control form-input" id="password-for-connection" placeholder="Password" autofocus size="15"> </div>' +
                        ' <div class="form-group"> <label for="otp-for-connection" class="sr-only">One-time Code</label>' +
                        ' <input type="text" class="form-control form-input" id="otp-for-connection" placeholder="One-time Code" maxlength="20" size="15"> </div>');
                } else {
                    $('#input_credential_modal .modal-body').html('<p>In order to download data, you need to reconnect to host <b id="host-for-connection"></b> again.</p>' +
                        ' <div class="form-group"><label for="password-for-connection" class="sr-only">Password</label>' +
                        ' <input type="password" class="form-control form-input" id="password-for-connection" placeholder="Password" autofocus size="15"></div>');
                }

                $('#host-for-connection').text(hostname);

                $('#input_credential_modal').modal({
                    keyboard: false,
                    backdrop: 'static'
                });
                
                $('#input_credential_submit').click(function (event) {
                    event.preventDefault();
                    if (is_otp) {
                        var data = JSON.stringify({
                            "username": username, "otc": $('#otp-for-connection').val(), "password": $('#password-for-connection').val(), "hostname": hostname, "port": port, "source": source
                        });
                    } else {
                        var data = JSON.stringify({
                            "username": username, "otc": "", "password": $('#password-for-connection').val(), "hostname": hostname, "port": port, "source": source
                        });
                    }

                    ajaxAsyncCall({
                        type: "POST",
                        url: "/sftp/connect",
                        data: data,
                        contentType: 'application/json; charset=utf-8',
                        statusCode: {
                            200: function () {
                                $('#input_credential_modal').modal('hide');
                                showInfoAlertInTop(source, "Downloading is started.");

                                var iframe = document.getElementById('download_iframe');
                                iframe.src = url;
                            },
                            400: function () {
                                
                            }
                        },
                        error: function (jqXhR, textStatus, errorThrown) {
                            if (!(errorThrown && errorThrown == "abort")) {
                                showErrorAlertInTop(source, jqXhR.responseText, errorThrown, "Can't download now.");
                            }
                        }
                    });

                });
            },
            406: function () {
                showWarningAlertInTop(url.substring(url.lastIndexOf('=') + 1), "You are allowed to download a file or a folder at a time from a server.");
            }
        }
    });
}

function createTable(content, path, source) {
    var settings = getTableSettings(content, source, path);
    if (source == 'host1') {
        host1_table = $("#host1-table").dataTable(settings);
    } else if (source == 'host2') {
        host2_table = $("#host2-table").dataTable(settings);
    }
}

function enable_waiting_box(message) {
    $('#gray-screen').css({'display': 'block', opacity: 0.3, 'width': $(document).width(), 'height': $(document).height()});
    $('body').css({'overflow': 'hidden'});
    var waiting_box = $('#waiting-box');
    waiting_box.css({'display': 'block'});
    waiting_box.find('p').text(message);
}

function disable_waiting_box() {
    $('#gray-screen').css({'display': 'none'});
    $('#waiting-box').css({'display': 'none'});
    $('body').css({'overflow':'auto'});
}

function fetch_table(source) {
    if (source == 'host1') {
        return host1_table;
    }
    if (source == 'host2') {
        return host2_table;
    }
}

function set_table(source, table) {
    if (source == 'host1') {
        host1_table = table;
    }
    if (source == 'host2') {
        host2_table = table;
    }
}

function extractPath(href) {
    var path = href.substring(href.indexOf("?") + 1, href.indexOf("&"));
    return path.substring(path.indexOf("=") + 1);
}

function disconnect_sftp(source) {
    if (source == 'host1' || source == 'host2') {
        ajaxAsyncCall(
            {
                type: "DELETE",
                url: "/sftp/disconnect?source=" + source,
                statusCode: {
                    200: function () {
                        fetch_table(source).api().destroy();
                        $("#" + source + "-table").empty();
                        $("#" + source + "-table-div").html("");
                        $("#" + source + "-path").html("");
                        $("#" + source + "-disconnect-btn").css("display", "none");
                        $("#" + source + "-submit-btn").css("display", "inline-block");
                        $("#" + source + "-username").prop("disabled", false);
                        $("#" + source + "-hostname").prop("disabled", false);
                        $("#" + source + "-port").prop("disabled", false);
                    }
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    if (!(errorThrown && errorThrown == "abort")) {
                        showErrorAlertInTop(source, jqXHR.responseText, errorThrown, 'Disconnection failed.');
                    }
                }
            }
        );
    }
}

function logout() {
    $.ajax({
        type: "DELETE",
        url: "/sftp/disconnect"
    });
}

function getTableSettings(content, source, path) {
    return {
        "pagingType": "simple",
        "dom": "tlp",
        "language": {
            "paginate": {
                "next": "&gt;",
                "previous": "&lt;"
            }
        },
        "info": false,
        "searching": false,
        "lengthMenu": [[10, 25, 40, -1], [10, 25, 40, "All"]],
        "data": content,
        "columns": [{
            "title": "Name",
            "render": function (data, type, full, meta) {
                var isFoler = full[2];
                if (isFoler == "folder") {
                    if (path == "/") {
                        return '<i class="fa fa-folder-o"></i> <a class="' + source + '-folder-link" href="/sftp/list?path=' + path + data + '&source=' + source + '">' + data + '</a>';
                    } else {
                        return '<i class="fa fa-folder-o"></i> <a class="' + source + '-folder-link" href="/sftp/list?path=' + path + '/' + data + '&source=' + source + '">' + data + '</a>';
                    }
                } else {
                    return '<i class="fa fa-file-o"></i> ' + data;
                }
            }
        }, {
            "orderable": false,
            "title": "",
            "render": function (data, type, full, meta) {
                var isFoler = full[2];
                var name = full[0];
                var downloadUrl;
                if (isFoler == "folder") {
                    if (path == "/") {
                        downloadUrl = "/sftp/zip?path=" + path + name + "&source=" + source;
                    } else {
                        downloadUrl = "/sftp/zip?path=" + path + "/" + name + "&source=" + source;
                    }
                } else {
                    if (path == "/") {
                        downloadUrl = "/sftp/download?path=" + path + name + "&source=" + source;
                    } else {
                        downloadUrl = "/sftp/download?path=" + path + "/" + name + "&source=" + source;
                    }
                }
                return '<button class="download" onclick="downloadFile(\'' + downloadUrl + '\')"/><i class="fa fa-download"></i></button>';
            }
        }, {
            "title": "Size",
            "render": function (data, type, full, meta) {
                return convertBytes(full[1]);
            }
        }]
    };
}

function reloadTableData(content, path, source) {
    fetch_table(source).api().destroy();
    $("#" + source + "-table").empty();
    $("#" + source + "-table-div").html('<table id="' + source + '-table" class="table table-striped"></table>');
    var settings = getTableSettings(content, source, path);
    set_table(source, $("#" + source + "-table").dataTable(settings));
}

function refresh_target_host(target) {
    var href;
    if (target == 'host1') {
        href = $(".host1-path-link").last().attr("href");
    }
    if (target == 'host2') {
        href = $(".host2-path-link").last().attr("href");
    }
    ajaxAsyncCall(
        {
            type: "GET",
            url: href,
            dataType: "json",
            statusCode: {
                200: function (returnedData) {
                    reloadTableData(returnedData["data"], returnedData["path"], target);
                }
            },
            error: function (returnedData) {
                //TODO
            }
        }
    );
}

function uploadData(eventData) {
    var target = eventData.data['target'];
    uploaded_files_array = [];
    upload_progress_bar_group = {};
    upload_target = target;
    uploaded_files_number = 0;
    successful_uploaded_files_number = 0;
    $('#upload_progress_group').empty();
    $('#upload_modal').modal({
        keyboard: false,
        backdrop: 'static'
    });
}

function deleteData(eventData) {
    var target = eventData.data['target'];

    var selected_items;
    if (target == "host1") {
        $(".host1-menu").hide();
        selected_items = host1_table.api().rows('.selected').data();
    } else if (target == "host2") {
        $(".host2-menu").hide();
        selected_items = host2_table.api().rows('.selected').data();
    }

    if (selected_items.length == 0) {
        showWarningAlertInTop(target, "No files or folders are selected.");
    } else {
        $('#confirm_modal h4').text("Are you sure to delete selected items?");
        $('#confirm_modal').modal({keyboard: false});

        $('#confirm_modal_button').one('click', {"target": target, "selected_items": selected_items}, confirmDelete);
    }
}

function confirmDelete(eventData) {

    var target = eventData.data['target'];
    var transferredData = [];

    var selected_items = eventData.data['selected_items'];


    selected_items.each(function (item) {
        transferredData.push({"name": item[0], "type": item[2]});
    });

    var path;
    if (target == "host1") {
        path = extractPath($('.host1-path-link:last').attr('href'));
    } else if (target == "host2") {
        path = extractPath($('.host2-path-link:last').attr('href'));
    }

    ajaxAsyncCall({
        type: "DELETE",
        url: "/sftp/delete",
        data: JSON.stringify({"source": target, "path": path, "data": transferredData}),
        contentType: 'application/json; charset=utf-8',
        statusCode: {
            204: function () {
                refresh_target_host(target);
            }
        },
        error: function (jqXhR, textStatus, errorThrown) {
            if (!(errorThrown && errorThrown == "abort")) {
                showErrorAlertInTop(target, jqXhR.responseText, errorThrown, "Deletion failed.");
            }
        }
    });
}

function transferData(eventData) {
    var target = eventData.data['target'];

    var fileData = [];
    var folderData = [];

    var selected_items;
    if (target == "host1") {
        selected_items = host1_table.api().rows('.selected').data();
    } else if (target == "host2") {
        selected_items = host2_table.api().rows('.selected').data();
    }

    if (selected_items.length == 0) {
        showWarningAlertInTop(target, "No files or folders are selected.");
    } else {
        selected_items.each(function (item) {
            if (item[2] == 'file') {
                fileData.push(item[0]);
            }
            if (item[2] == 'folder') {
                folderData.push(item[0]);
            }
        });

        var from_path;
        var to_path;
        if (target == "host1") {
            from_path = extractPath($('.host1-path-link:last').attr('href'));
            to_path = extractPath($('.host2-path-link:last').attr('href'));
        } else if (target == "host2") {
            from_path = extractPath($('.host2-path-link:last').attr('href'));
            to_path = extractPath($('.host1-path-link:last').attr('href'));
        }

        var transferredData;
        if (target == "host1") {
            transferredData = JSON.stringify({
                "from": {"path": from_path, "name": "host1", "data": {"file": fileData, "folder": folderData}},
                "to": {"path": to_path, "name": "host2"}
            });
        } else if (target == "host2") {
            transferredData = JSON.stringify({
                "from": {"path": from_path, "name": "host2", "data": {"file": fileData, "folder": folderData}},
                "to": {"path": to_path, "name": "host1"}
            })
        }

        ajaxAsyncCall({
            type: "POST",
            url: "/sftp/transfer/prepare",
            data: transferredData,
            dataType: "json",
            contentType: 'application/json; charset=utf-8',
            statusCode: {
                302: function (returnedData) {
                    var information;
                    if (returnedData['responseJSON']['file']) {
                        information = "File " + returnedData['responseJSON']['file'];
                    }
                    if (returnedData['responseJSON']['folder']) {
                        if (information) {
                            information += " and Folder " + returnedData['responseJSON']['folder'] + " are existing, please rename them before transferring.";
                        } else {
                            information = "Folder " + returnedData['responseJSON']['folder'] + " is existing, please rename it before transferring.";
                        }
                    } else {
                        information += " is existing, please rename it before transferring."
                    }
                    showWarningAlertInTop(target, information);
                },
                200: function (returnedData) {
                    var credential_for_origin_otc = $('#credential-for-origin-otc');
                    var credential_for_origin_password = $('#credential-for-origin-password');
                    var credential_for_dest_otc = $('#credential-for-dest-otc');
                    var credential_for_dest_password = $('#credential-for-dest-password');

                    var notification_email_form = $('#notification-email-form');
                    var credential_for_origin_form = $('#credential-for-origin-form');
                    var credential_for_dest_form = $('#credential-for-dest-form');

                    var host1_hostname = $('#host1-hostname').val();
                    var host2_hostname = $('#host2-hostname').val();
                    if (target == "host1") {
                        $('#credential-for-origin').text(host1_hostname);
                        $('#credential-for-dest').text(host2_hostname);

                        if (host_info['otp_hosts'].includes(host1_hostname)) {
                            credential_for_origin_otc.removeClass('hidden');
                            credential_for_origin_otc.val('');
                        } else {
                            credential_for_origin_otc.addClass('hidden');
                        }

                        if (host_info['otp_hosts'].includes(host2_hostname)) {
                            credential_for_dest_otc.removeClass('hidden');
                            credential_for_dest_otc.val('');
                        } else {
                            credential_for_dest_otc.addClass('hidden');
                        }

                    } else if (target == "host2") {
                        $('#credential-for-origin').text(host2_hostname);
                        $('#credential-for-dest').text(host1_hostname);

                        if (host_info['otp_hosts'].includes(host2_hostname)) {
                            credential_for_origin_otc.removeClass('hidden');
                            credential_for_origin_otc.val('');
                        } else {
                            credential_for_origin_otc.addClass('hidden');
                        }

                        if (host_info['otp_hosts'].includes(host1_hostname)) {
                            credential_for_dest_otc.removeClass('hidden');
                            credential_for_dest_otc.val('');
                        } else {
                            credential_for_dest_otc.addClass('hidden');
                        }
                    }

                    credential_for_origin_password.val('');
                    credential_for_dest_password.val('');
                    $('#notification-email').val('');

                    if (notification_email_form.hasClass('hidden')) {
                        notification_email_form.removeClass('hidden');
                    }
                    if (!credential_for_origin_form.hasClass('hidden')) {
                        credential_for_origin_form.addClass('hidden');
                    }

                    if (!credential_for_dest_form.hasClass('hidden')) {
                        credential_for_dest_form.addClass('hidden');
                    }

                    $('#transfer_modal').modal({
                        keyboard: false,
                        backdrop: 'static'
                    });

                    $('#confirm-email-btn').click(function (event) {
                        var email = $('#notification-email').val();

                        notification_email_form.addClass('hidden');
                        credential_for_origin_form.removeClass('hidden');

                        $('#confirm-origin-btn').click(function(event){

                            var origin_username;
                            var origin_host;
                            var dest_username;
                            var dest_host;
                            if (target == "host1") {
                                origin_username = $('#host1-username').val();
                                origin_host = host1_hostname;
                                dest_username = $('#host2-username').val();
                                dest_host = host2_hostname;
                            }
                            if (target == "host2") {
                                origin_username = $('#host2-username').val();
                                origin_host = host2_hostname;
                                dest_username = $('#host1-username').val();
                                dest_host = host1_hostname;
                            }

                            var origin_password = credential_for_origin_password.val();
                            var origin_otc = host_info['otp_hosts'].includes(origin_host) ? credential_for_origin_otc.val() : '';

                            ajaxAsyncCall({
                                type: "POST",
                                url: "/sftp/transfer/register",
                                data: JSON.stringify({"username": origin_username, "password": origin_password, "hostname": origin_host, "otc": origin_otc, "port": 22, "email": email}),
                                contentType: 'application/json; charset=utf-8',
                                statusCode: {
                                    200: function () {
                                        credential_for_origin_form.addClass('hidden');
                                        credential_for_dest_form.removeClass('hidden');

                                        $('#confirm-transfer-btn').click(function (event) {
                                            var dest_password = $('#credential-for-dest-password').val();
                                            var dest_otc = host_info['otp_hosts'].includes(dest_host) ? $('#credential-for-dest-otc').val() : '';

                                            ajaxAsyncCall({
                                                type: "POST",
                                                url: "/sftp/transfer/job/submit",
                                                data: JSON.stringify({"data": returnedData, "username": dest_username, "password": dest_password, "hostname": dest_host, "otc": dest_otc, "port": 22, "email": email, "from": {"hostname": origin_host, "path": from_path}, "to": {"hostname": dest_host, "path": to_path}}),
                                                contentType: 'application/json; charset=utf-8',
                                                statusCode: {
                                                    200: function () {
                                                        $('#transfer_modal').modal('hide');
                                                        showInfoAlertInTop(target, "The task of data transfer is submitted.");
                                                    },
                                                    400: function () {

                                                    },
                                                    404: function () {

                                                    },
                                                    500: function () {

                                                    }
                                                }
                                            });
                                        });
                                    },
                                    403: function () {

                                    },
                                    400: function () {

                                    },
                                    500: function () {

                                    }
                                }
                            });
                        });
                    });
                }
            },
            error: function (jqXhR, textStatus, errorThrown) {
                if (!(errorThrown && errorThrown == "abort")) {
                    showErrorAlertInTop(target, jqXhR.responseText, errorThrown, "Can't transfer data.");
                }
            }
        });
    }
}

function clickOnPath(event) {
    var target = event.data['target'];

    event.preventDefault();
    var href = $(this).attr('href');

    ajaxAsyncCall({
        type: "GET",
        url: href,
        dataType: "json",
        statusCode: {
            200: function (returnedData) {
                var path = returnedData["path"];
                if (target == "host1") {
                    $(".host1-path-link").each(function () {
                        if (extractPath($(this).attr('href')).length > path.length) {
                            $(this).remove();
                        }
                    });
                } else if (target == "host2") {
                    $(".host2-path-link").each(function () {
                        if (extractPath($(this).attr('href')).length > path.length) {
                            $(this).remove();
                        }
                    });
                }
                reloadTableData(returnedData["data"], path, target);
            }
        },
        error: function (jqXhR, textStatus, errorThrown) {
            if (!(errorThrown && errorThrown == "abort")) {
                showErrorAlertInTop(target, jqXhR.responseText, errorThrown, "Can't list items.");
            }
        }
    });
}

function clickOnFolder(event) {
    var target = event.data['target'];

    event.preventDefault();
    var href = $(this).attr('href');
    var folder_name = $(this).text();

    ajaxAsyncCall({
        type: "GET",
        url: href,
        dataType: "json",
        statusCode: {
            200: function (returnedData) {
                var path = returnedData["path"];
                if (target == "host1") {
                    $("#host1-path").append('<a class="host1-path-link" href="/sftp/list?path=' + path + '&source=host1">' + folder_name + '/</a>');

                } else if (target == "host2") {
                    $("#host2-path").append('<a class="host2-path-link" href="/sftp/list?path=' + path + '&source=host2">' + folder_name + '/</a>');

                }

                reloadTableData(returnedData["data"], path, target);
            }
        },
        error: function (jqXhR, textStatus, errorThrown) {
            if (!(errorThrown && errorThrown == "abort")) {
                showErrorAlertInTop(target, jqXhR.responseText, errorThrown, "Can't show the folder's content.");
            }
        }
    });
}

function showContextMenu(event) {
    var target = event.data['target'];
    //prevent default context menu for right click
    event.preventDefault();

    var menu;
    if (target == 'host1') {
        menu = $(".host1-menu");
    } else if (target == 'host2') {
        menu = $(".host2-menu")
    }

    //hide menu if already shown
    menu.hide();

    //get x and y values of the click event
    var pageX = event.pageX;
    var pageY = event.pageY;

    //position menu div near mouse cliked area
    menu.css({top: pageY , left: pageX});

    var mwidth = menu.width();
    var mheight = menu.height();
    var screenWidth = $(window).width();
    var screenHeight = $(window).height();

    //if window is scrolled
    var scrTop = $(window).scrollTop();

    //if the menu is close to right edge of the window
    if(pageX+mwidth > screenWidth){
        menu.css({left:pageX-mwidth});
    }

    //if the menu is close to bottom edge of the window
    if(pageY+mheight > screenHeight+scrTop){
        menu.css({top:pageY-mheight});
    }

    //finally show the menu
    menu.show();
}

var create_folder_target;
var create_folder_path;
function showFolderModal(event) {
    var target = event.data['target'];
    create_folder_target = target;
    if (target == "host1") {
        create_folder_path = extractPath($('.host1-path-link:last').attr('href'));
    } else if (target == "host2") {
        create_folder_path = extractPath($('.host2-path-link:last').attr('href'));
    }
    var create_folder_modal = $('#create_folder_modal');
    create_folder_modal.modal({
        keyboard: false
    });
    create_folder_modal.on('shown.bs.modal', function () {
        var folder_name = $('#folder_name');
        folder_name.val('');
        folder_name.focus();
    });
}

var rename_target;
var rename_path;
var old_name;
function showRenameModal(event) {
    rename_target = event.data['target'];

    var selected_items;
    if (rename_target == "host1") {
        rename_path = extractPath($('.host1-path-link:last').attr('href'));
        selected_items = host1_table.api().rows('.selected').data();
    } else if (rename_target == "host2") {
        rename_path = extractPath($('.host2-path-link:last').attr('href'));
        selected_items = host2_table.api().rows('.selected').data();
    }

    if (selected_items.length <= 0) {
        showWarningAlertInTop(rename_target, "No file or folder is selected.");
    } else if (selected_items.length >= 2) {
        showWarningAlertInTop(rename_target, "You can't rename more than one at a time.");
    } else {
        selected_items.each(function (item) {
            if (item[2] == 'file') {
                old_name = item[0];
            }
            if (item[2] == 'folder') {
                old_name = item[0];
            }
        });

        var rename_modal = $('#rename_modal');
        rename_modal.modal({
            keyboard: false
        });
        rename_modal.on('shown.bs.modal', function () {
            var new_name = $('#new_name');
            new_name.val('');
            new_name.focus();
        });
    }
}

function convertBytes(size) {
    if (size == "0") {
        return size;
    } else {
        var i = Math.floor( Math.log(size) / Math.log(1024) );
        return ( size / Math.pow(1024, i) ).toFixed(2) * 1 + ' ' + ['B', 'kB', 'MB', 'GB', 'TB'][i];
    }
}

function showWarningAlertInTop(target, text) {
    var alert = $('#' + target + '-panel .alert');
    if (alert.length) {
        alert.remove();
    }
    $('.' + target + '-form').after(returnInsertedAlert(text));
}

function showInfoAlertInTop(target, text) {
    var alert = $('#' + target + '-panel .alert');
    if (alert.length) {
        alert.remove();
    }
    $('.' + target + '-form').after(returnInsertedInfoAlert(text));
}

function showErrorAlertInTop(target, text, errorThrown, extraMessage) {
    var alert = $('#' + target + '-panel .alert');
    if (alert.length) {
        alert.remove();
    }
    if (text) {
        var index = text.indexOf(':');
        if (index > 0) {
            $('.' + target + '-form').after(returnInsertedAlert(extraMessage + ' ' + text.substring(index + 1)));
        } else {
            $('.' + target + '-form').after(returnInsertedAlert(extraMessage + ' ' + text));
        }
    } else {
        $('.' + target + '-form').after(returnInsertedAlert(extraMessage + ' ' + errorThrown));
    }
}
function returnInsertedAlert(message) {
    return '<div class="alert alert-danger alert-dismissible fade in inserted" role="alert"> <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>' + message + '</div>';
}

function returnInsertedInfoAlert(message) {
    return '<div class="alert alert-success alert-dismissible fade in inserted" role="alert"> <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>' + message + '</div>';
}

