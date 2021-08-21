import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurNLPEntity } from '../../model/nlp-entity.model';
import { NotifierService } from 'angular-notifier';
import { TurNLPEntityService } from '../../service/nlp-entity.service';
import { ActivatedRoute } from '@angular/router';
import { FormControl, Validators } from '@angular/forms';

@Component({
  selector: 'nlp-entity-page',
  templateUrl: './nlp-entity-page.component.html'
})
export class TurNLPEntityPageComponent implements OnInit {
  private turNLPEntity: Observable<TurNLPEntity>;

  portControl = new FormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(private readonly notifier: NotifierService,
    private turNLPEntityService: TurNLPEntityService,
    private route: ActivatedRoute) {
    let id = this.route.snapshot.paramMap.get('id');
    this.turNLPEntity = this.turNLPEntityService.get(id);

  }

  getTurNLPEntity(): Observable<TurNLPEntity> {
    return this.turNLPEntity;
  }

  ngOnInit(): void {
  }

  public saveSite(_turNLPEntity: TurNLPEntity) {
    this.turNLPEntityService.save(_turNLPEntity).subscribe(
      (turNLPEntity: TurNLPEntity) => {
        _turNLPEntity = turNLPEntity;
        this.notifier.notify("success", turNLPEntity.name.concat(" NLP entity was updated."));
      },
      response => {
        this.notifier.notify("error", "NLP entity was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
