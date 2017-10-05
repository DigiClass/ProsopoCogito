define([
  'common/ui/formatting',
  'common/config',
  'document/annotation/common/editor/sections/section'
], function(Formatting, Config, Section) {

  var PersonSection = function(parent, personBody, personName) {
    var self = this,

        infoEl,

        element = (function() {
          var lastModified = jQuery(
                '<div class="last-modified">' +
                  '<a class="by" href="/' + personBody.last_modified_by + '">' +
                    personBody.last_modified_by + '</a>' +
                  '<span class="at">' +
                    Formatting.timeSince(personBody.last_modified_at) +
                  '</span>' +
                '</div>'),

              el = jQuery(
                '<div class="section category person">' +
                  '<div class="category-icon">&#xe863;</div>' +
                  '<div class="info"><span>Marked as a Person</span></div>' +
                '</div>');

          if (personBody.last_modified_by) {
            infoEl = el.find('.info');
            infoEl.append(lastModified);

            if (Config.writeAccess)
              infoEl.append('<button class="btn tiny delete icon">&#xf014;</button>');
          }

          parent.append(el);
          return el;
        })(),

        /** Updates the section with a change performed by the user **/
        update = function(diff) {
          var lastModified = { by: Config.me, at: new Date() };

          // Diffs contain uri and status info
          if (personBody.uri !== diff.uri && diff.uri) {
            // There's a new URI - update the place card
            // fillFromURI(diff.uri, diff.status, lastModified);
            var uri = jQuery(
              '<div>' +
                '<p>' + diff.uri + '<p>' +
              '<div>');
            infoEl.append(uri);
          }
          // else if (!diff.uri) {
          //   // There's no URI now (but there was one before!) - change to 'No Match' card
          //   renderNoMatchCard(diff.status, lastModified);
          // }

          // Queue these updates for deferred storage
          // queuedUpdates.push(function() {
          //   delete personBody.last_modified_by;
          //   delete personBody.last_modified_at;
          //   personBody.status = diff.status;
          //   if (diff.uri)
          //     personBody.uri = diff.uri;
          //   else
          //     delete personBody.uri;
          // });
        },

        destroy = function() {
          element.remove();
        };

    element.on('click', '.delete', function() { self.fireEvent('delete'); });
    element.on('click', '.category-icon', function() { self.fireEvent('searchPerson'); });

    this.body = personBody;
    this.update = update;
    this.destroy = destroy;
    this.hasChanged = function() { return false; };
    this.commit = function() {}; // Not (yet) needed

    Section.apply(this);
  };
  PersonSection.prototype = Object.create(Section.prototype);

  return PersonSection;

});
