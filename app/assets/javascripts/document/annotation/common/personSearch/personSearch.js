define([
  'common/api',
  'common/hasEvents'
], function(API, HasEvents) {
  var people;
  jQuery.getJSON('/assets/people/test-snap.json').done(function(p) {
    people = p;
  });

  var personSearch = function() {

    var self = this,

        personBody,

        element = jQuery(
          '<div class="clicktrap">' +
            '<div class="modal-wrapper georesolution-wrapper">' +
              '<div class="modal georesolution-panel">' +
                '<div class="modal-header">' +
                  // search box
                  '<div class="georesolution-search">' +
                    '<input class="search inline" type="text" placeholder="Search for a Person..." />' +
                    '<button class="search icon">&#xf002;</button>' +
                  '</div>' +
                  '<button class="nostyle outline-icon cancel">&#xe897;</button>' +
                '</div>' +
                '<div class="modal-body">' +
                  '<div class="georesolution-sidebar">' +
                    '<div class="result-header">'+
                      '<div class="result-total">' +
                        '<span class="icon">&#xf03a;</span> <span class="label"></span>' +
                      '</div>' +
                      '<div class="result-took"></div>' +
                    '</div>' +
                    '<div class="result-list">' +
                      // list items for people
                      '<ul class="peopleResults">' +
                      '</ul>' +
                      '<div class="wait-for-next">' +
                        '<img src="/assets/images/wait-circle.gif">' +
                      '</div>' +
                    '</div>' +
                  '</div>' +
              '</div>' +
            '</div>' +
          '</div>'
        ).appendTo(document.body).hide(),

        open = function (body) {
          personBody = body;

          element.show();
        },

        searchInput     = element.find('.search'),

        searchListResults   = element.find('.peopleResults'),

        onSaveMySearch = function(e) {
          var li = jQuery(e.target).closest('li');

          close();
          self.fireEvent('save', personBody, { uri: jQuery(li).data('uri') });
        },

        close = function() {
          personBody = false;
          // closeUnlocatedPopup();
          // waitForNext.hide();
          element.hide();
        };

        searchInput.keyup(function(e) {
          // TODO Improve search
          // if (e.which === 13) {
          //   clear();
          //   currentSearch = searchInput.val().trim();
          //   if (currentSearch.length === 0)
          //     currentSearch = false;
          //   search();
          // }
          jQuery('.listItem').remove();
          var searchResults = people.filter(function(p) {
            if (
              p.names.some(function(n) {
                return n.name.toLowerCase().indexOf(searchInput.val().trim()) > -1;
              })
            ){
              return true;
            }
            return false;
          });

          searchResults.forEach(function(p) {
            var listItem = '<li class="listItem" data-uri="' + p.URI + '">' + p.names[0].name + '</li>';
            searchListResults.append(listItem);
          });

        });

        jQuery('.peopleResults').on('click', onSaveMySearch);

        this.open = open;
        HasEvents.apply(this);
  };
  personSearch.prototype = Object.create(HasEvents.prototype);

  return personSearch;
});
