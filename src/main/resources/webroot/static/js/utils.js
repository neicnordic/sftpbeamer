/**
 * Created by Xiaxi Li on 26/Aug/2015.
 */
var host1_table;
var host2_table;
var server_info;

var transfer_target;
var upload_target;
var host_upload_reference;
var uploaded_files_array;
var progress_bar_group;
var upload_url;
var host1_upload_url;
var host2_upload_url;

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
                "title": "Size",
                "render": function (data, type, full, meta) {
                    return convertBytes(data);
                }
            }, {
                "visible": false
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
                "title": "Size",
                "render": function (data, type, full, meta) {
                    return convertBytes(data);
                }
            }, {
                "visible": false
            }]
        });
    }
}

function enable_waiting_box(message) {
    $('#gray-screen').css({'display': 'block', opacity: 0.3, 'width': $(document).width(), 'height': $(document).height()});
    $('body').css({'overflow': 'hidden'});
    $('#waiting-box').css({'display': 'block'});
    $('#waiting-box > p').text(message);
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
        endpoint = 'wss://' + server_info['name'] + ':' + server_info['ws_port'] + '/ws';
    } else {
        endpoint = 'ws://' + server_info['name'] + ':' + server_info['ws_port'] + '/ws';
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

function change_modal_property(modal_title, modal_content) {
    $('#info_modal_label').text(modal_title);

    $('#info_modal .modal-body p').text(modal_content);
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

// str generateId(int len);
//   len - must be an even number (default: 40)
function generateId(len) {
    var arr = new Uint8Array((len || 40) / 2);
    window.crypto.getRandomValues(arr);
    return [].map.call(arr, function(n) { return n.toString(16); }).join("");
}

function extractPath(href) {
    var path = href.substring(href.indexOf("?") + 1, href.indexOf("&"));
    return path.substring(path.indexOf("=") + 1);
}

function disconnect_sftp(source) {
    if (source == 'host1' || source == 'host2') {
        $.ajax({
            type: "DELETE",
            url: "/sftp/disconnect?source=" + source,
            success: function () {
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
        });
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
            "title": "Size",
            "render": function (data, type, full, meta) {
                return convertBytes(data);
            }
        }, {
            "visible": false
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
    $.ajax({
        type: "GET",
        url: href,
        dataType: "json",
        success: function (returnedData) {
            var path = returnedData["path"];
            reloadTableData(returnedData["data"], path, target);
        }
    });
}

function createUploadUrl(target) {
    var schema;
    if (server_info['ssl']) {
        schema = "https";
    } else {
        schema = "http";
    }
    if (target == 'host1') {
        return schema + "://" + server_info['name'] + ":" + server_info['upload_port'] + "/upload?path=" + extractPath($(".host1-path-link").last().attr("href"));
    }
    if (target == 'host2') {
        return schema + "://" + server_info['name'] + ":" + server_info['upload_port'] + "/upload?path=" + extractPath($(".host2-path-link").last().attr("href"));
    }
}

function getUploadReference(eventData) {
    var target = eventData.data['target'];
    $.ajax({
        url: "/sftp/upload/reference?source=" + target,
        method: "GET",
        contents: "text/plain",
        success: function(reference){
            host_upload_reference = reference;
            uploaded_files_array = [];
            progress_bar_group = {};
            upload_target = target;
            upload_url = host1_upload_url;
            $('#upload_progress_group').empty();
            $('#upload_modal').modal({
                keyboard: false
            });
        }
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
        change_modal_property("Information", "No files or folders are selected.");
        $('#info_modal').modal({
            keyboard: false
        });
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


    $.ajax({
        type: "DELETE",
        url: "/sftp/delete",
        data: JSON.stringify({"source": target, "path": path, "data": transferredData}),
        contentType: 'application/json; charset=utf-8',
        statusCode: {
            200: function () {
                var url = "/sftp/list?path=" + path + "&source=" + target;
                $.ajax({
                    type: "GET",
                    url: url,
                    dataType: "json",
                    success: function (updatedData) {
                        reloadTableData(updatedData["data"], updatedData["path"], target);
                    }
                });
            },
            500: function (returnedData) {
                if (returnedData["error"]) {
                    change_modal_property("Error", returnedData["error"]);
                    var modal = $('#info_modal');
                    modal.one('hide.bs.modal', function (event) {
                        location.reload();
                    });
                    modal.modal({
                        keyboard: false
                    });
                } else if (returnedData["exception"]) {
                    change_modal_property("Exception", returnedData["exception"]);
                    $('#info_modal').modal({
                        keyboard: false
                    });
                }
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
        change_modal_property("Information", "No files or folders are selected.");
        $('#info_modal').modal({
            keyboard: false
        });
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

        $.ajax({
            type: "POST",
            url: "/sftp/transfer/prepare",
            data: transferredData,
            dataType: "json",
            contentType: 'application/json; charset=utf-8',
            success: function (returnedData) {
                $('#transfer_progress').css("display", "block");
                $('#transfer_progress_group').empty();
                $('#transfer_modal').modal({
                    keyboard: false
                });

                var messageAddress = generateId(40);
                if (target == "host1") {
                    transfer_target = "host2";

                    transferredData = JSON.stringify({
                        "address": messageAddress,
                        "from": {"path": from_path, "name": "host1"},
                        "to": {"path": to_path, "name": "host2"},
                        "data": returnedData
                    });
                } else if (target == "host2") {
                    transfer_target = "host1";

                    transferredData = JSON.stringify({
                        "address": messageAddress,
                        "from": {"path": from_path, "name": "host2"},
                        "to": {"path": to_path, "name": "host1"},
                        "data": returnedData
                    })
                }


                var ws = create_ws_connection();
                ws.onopen = function () {
                    ws.send(JSON.stringify({
                        "address": messageAddress}));
                };
                ws.onmessage = function (event) {
                    var message = JSON.parse(event.data);
                    if (message["status"] == "connected") {
                        $.ajax({
                            type: "POST",
                            url: "/sftp/transfer/start",
                            data: transferredData,
                            dataType: "json",
                            contentType: 'application/json; charset=utf-8',
                            success: function (returnedData) {
                            }
                        });
                    }
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
                    if (message["status"] == "finish") {
                        $('#transfer_progress').css("display", "none");
                    }
                    if (message["status"] == "error") {
                        // change_modal_property("Information", "File transfer is done.");
                        // $('#info_modal').modal({
                        //     keyboard: false
                        // });
                    }
                };
                ws.onclose = function () {};
            }
        });

    }
}

function clickOnPath(event) {
    var target = event.data['target'];

    event.preventDefault();
    var href = $(this).attr('href');

    $.ajax({
        type: "GET",
        url: href,
        dataType: "json",
        success: function(returnedData) {
            if (returnedData["error"]) {
                change_modal_property("Error", returnedData["error"]);
                var modal = $('#info_modal');
                modal.one('hide.bs.modal', function (event) {
                    location.reload();
                });
                modal.modal({
                    keyboard: false
                });
            } else if (returnedData["exception"]) {
                change_modal_property("Exception", returnedData["exception"]);
                $('#info_modal').modal({
                    keyboard: false
                });
            } else {
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
                host1_upload_url = createUploadUrl(target);
                reloadTableData(returnedData["data"], path, target);
            }
        }
    });
}

function clickOnFolder(event) {
    var target = event.data['target'];

    event.preventDefault();
    var href = $(this).attr('href');
    var folder_name = $(this).text();
    $.ajax({
        type: "GET",
        url: href,
        dataType: "json",
        success: function(returnedData) {
            if (returnedData["error"]) {
                change_modal_property("Error", returnedData["error"]);
                var modal = $('#info_modal');
                modal.one('hide.bs.modal', function (event) {
                    location.reload();
                });
                modal.modal({
                    keyboard: false
                });
            } else if (returnedData["exception"]) {
                change_modal_property("Exception", returnedData["exception"]);
                $('#info_modal').modal({
                    keyboard: false
                });
            } else {
                var path = returnedData["path"];
                if (target == "host1") {
                    $("#host1-path").append('<a class="host1-path-link" href="/sftp/list?path=' + path + '&source=host1">' + folder_name + '/</a>');
                    host1_upload_url = createUploadUrl("host1");
                } else if (target == "host2") {
                    $("#host2-path").append('<a class="host2-path-link" href="/sftp/list?path=' + path + '&source=host2">' + folder_name + '/</a>');
                    host2_upload_url = createUploadUrl("host2");
                }

                reloadTableData(returnedData["data"], path, target);
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
    $('#create_folder_modal').modal({
        keyboard: false
    });
    $('#create_folder_modal').on('shown.bs.modal', function () {
        $('#folder_name').focus();
    });
}

function convertBytes(size) {
    if (size == "0") {
        return size;
    } else {
        var i = Math.floor( Math.log(size) / Math.log(1024) );
        return ( size / Math.pow(1024, i) ).toFixed(2) * 1 + ' ' + ['B', 'kB', 'MB', 'GB', 'TB'][i];
    }
}
