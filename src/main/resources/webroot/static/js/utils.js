/**
 * Created by Xiaxi Li on 26/Aug/2015.
 */
var host1_table;
var host2_table;
var server_info;

var transfer_target;
var upload_target;
var uploaded_files_array;
var progress_bar_group;

var current_ajax_request;

function ajaxAsyncCall(settings) {
    if (current_ajax_request) {
        current_ajax_request.abort();
        current_ajax_request = null;
    }
    current_ajax_request = $.ajax(settings);
}

function createTable(name, home, content) {
    if (name == 'host1') {
        host1_table = $("#host1-table").dataTable({
            "pagingType": "simple",
            "dom": "tlp",
            "language": {
                "paginate": {
                    "previous": "&laquo;",
                    "next": "&raquo;"
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
                        return '<i class="fa fa-folder-o"></i> <a class="host1-folder-link" href="/sftp/list?path=' + home + '/' + data + '&source=host1"> ' + data + '</a>';
                    } else {
                        return '<i class="fa fa-file-o"></i> ' + data;
                    }
                }
            }, {
                "title": "",
                "render": function (data, type, full, meta) {
                    var isFoler = full[2];
                    var name = full[0];
                    if (isFoler == "folder") {
                        return '<a target="_blank" href="/sftp/zip?path=' + home + '/' + name + '&source=host1"><i class="fa fa-download"></i></a>';
                    } else {
                        return '<a target="_blank" href="/sftp/download?path=' + home + '/' + name + '&source=host1"><i class="fa fa-download"></i></a>';
                    }
                },
                "orderable": false
            }, {
                "title": "Size",
                "render": function (data, type, full, meta) {
                    return convertBytes(full[1]);
                }
            }]
        });
    } else if (name == 'host2') {
        host2_table = $("#host2-table").dataTable({
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
                    var isFolder = full[2];
                    if (isFolder == "folder") {
                        return '<i class="fa fa-folder-o"></i> <a class="host2-folder-link" href="/sftp/list?path=' + home + '/' + data + '&source=host2"> ' + data + '</a>';
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
                    if (isFoler == "folder") {
                        return '<a target="_blank" href="/sftp/zip?path=' + home + '/' + name + '&source=host2"><i class="fa fa-download"></i></a>';
                    } else {
                        return '<a target="_blank" href="/sftp/download?path=' + home + '/' + name + '&source=host2"><i class="fa fa-download"></i></a>';
                    }
                }
            }, {
                "title": "Size",
                "render": function (data, type, full, meta) {
                    return convertBytes(full[1]);
                }
            }]
        });
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

function create_ws_connection() {
    // This is for local testing
    var endpoint
    if (server_info['ssl']) {
        endpoint = 'wss://' + server_info['name'] + ':' + server_info['ws_port'] + '/sftp/ws';
    } else {
        endpoint = 'ws://' + server_info['name'] + ':' + server_info['ws_port'] + '/sftp/ws';
    }
    // This is for testing server without ssl
    // var endpoint = 'ws://tryggve.cbu.uib.no:80/websocket';
    //This is for testing server with ssl
    //var endpoint = 'wss://tryggve.cbu.uib.no:443/websocket';

    return new WebSocket(endpoint);
}

function refresh_progress_bar(message) {
    var transfer_progress_bar = $('.progress-bar');
    var transferred_bytes = Number(message["transferred_bytes"]);
    var total_bytes = Number(message["total_bytes"]);
    var file_name = message["file_name"];
    if (transferred_bytes == total_bytes) {
        transfer_progress_bar.css("width", '100%');
        transfer_progress_bar.text("100%");
    } else {
        var percentage = Math.round(transferred_bytes / total_bytes * 100);
        transfer_progress_bar.css("width", percentage + '%');
        transfer_progress_bar.text(percentage + '%');
    }
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

function reloadTableData(updatedData, path, source) {
    fetch_table(source).api().destroy();
    $("#" + source + "-table").empty();
    $("#" + source + "-table-div").html('<table id="' + source + '-table" class="table table-striped"></table>');
    var settings = {
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
        "data": updatedData,
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
                if (isFoler == "folder") {
                    if (path == "/") {
                        return '<a target="_blank" href="/sftp/zip?path=' + path + name + '&source=' + source + '"><i class="fa fa-download"></i></a>';
                    } else {
                        return '<a target="_blank" href="/sftp/zip?path=' + path + '/' + name + '&source=' + source + '"><i class="fa fa-download"></i></a>';
                    }
                } else {
                    if (path == "/") {
                        return '<a target="_blank" href="/sftp/download?path=' + path + name + '&source=' + source + '"><i class="fa fa-download"></i></a>';
                    } else {
                        return '<a target="_blank" href="/sftp/download?path=' + path + '/' + name + '&source=' + source + '"><i class="fa fa-download"></i></a>';
                    }
                }
            }
        }, {
            "title": "Size",
            "render": function (data, type, full, meta) {
                return convertBytes(full[1]);
            }
        }]
    };
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
    progress_bar_group = {};
    upload_target = target;
    $('#upload_progress_group').empty();
    $('#upload_modal').modal({
        keyboard: false
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
                200: function (returnedData) {
                    $('#transfer_progress').css("display", "block");
                    $('#transfer_progress_group').empty();
                    $('#transfer_modal').modal({
                        keyboard: false
                    });


                    if (target == "host1") {
                        transfer_target = "host2";

                        transferredData = JSON.stringify({
                            "from": {"path": from_path, "name": "host1"},
                            "to": {"path": to_path, "name": "host2"},
                            "data": returnedData
                        });
                    } else if (target == "host2") {
                        transfer_target = "host1";

                        transferredData = JSON.stringify({
                            "from": {"path": from_path, "name": "host2"},
                            "to": {"path": to_path, "name": "host1"},
                            "data": returnedData
                        })
                    }


                    var ws = create_ws_connection();
                    ws.onopen = function (event) {
                        ws.send(transferredData);
                    };
                    ws.onmessage = function (event) {
                        var message = JSON.parse(event.data);
                        if (message["status"] == "start") {
                            $('.progress-bar').text("0%");
                            $('#transferred-file-name').text(message["file"])
                        }
                        if (message["status"] == "transferring") {
                            refresh_progress_bar(message);
                        }
                        if (message["status"] == "done") {
                            var progress_bar = $('.progress-bar');
                            progress_bar.css("width", '0');
                            progress_bar.text("0%");
                            $("#transfer_progress_group").append('<p>' + message["file"] + '&nbsp; <i class="fa fa-check" aria-hidden="true" style="color: green;"></i></p>');
                            $('[data-spy="scroll"]').each(function () {
                                $(this).scrollspy('refresh')
                            });
                        }
                        if (message["status"] == "failed") {
                            var progress_bar = $('.progress-bar');
                            progress_bar.css("width", '0');
                            progress_bar.text("0%");
                            $("#transfer_progress_group").append('<p>' + message["file"] + '&nbsp; <i class="fa fa-times" aria-hidden="true" style="color: red;"></i></p>');
                            $('[data-spy="scroll"]').each(function () {
                                $(this).scrollspy('refresh')
                            });
                        }
                        if (message["status"] == "finish") {
                            $('#transfer_progress').css("display", "none");
                        }
                        if (message["status"] == "error") {
                            $('#transfer_modal').modal('hide');
                            showErrorAlertInTop(target, message["message"], "", "Can't transfer data.");
                        }
                    };
                    ws.onclose = function () {
                    };
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
    return '<div class="alert alert-danger alert-dismissible fade in error" role="alert"> <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>' + message + '</div>';
}
